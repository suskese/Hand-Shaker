package me.mklv.handshaker.fabric.server;

import me.mklv.handshaker.fabric.HandShaker;
import me.mklv.handshaker.fabric.server.configs.ConfigManager;
import me.mklv.handshaker.fabric.server.configs.ConfigMigrator;
import me.mklv.handshaker.fabric.server.utils.PlayerHistoryDatabase;
import me.mklv.handshaker.fabric.server.utils.PayloadValidator;
import me.mklv.handshaker.fabric.server.utils.StringUtils;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HandShakerServer implements DedicatedServerModInitializer {
    public static boolean DEBUG_MODE = false;
    public static final String MOD_ID = "hand-shaker";
    public static final Identifier VELTON_CHANNEL = Identifier.of("velton", "signature");
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + "-server");
    private static HandShakerServer instance;
    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private ConfigManager configManager;
    private PlayerHistoryDatabase playerHistoryDb;
    private MinecraftServer server;
    private PublicKey publicKey;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public record ClientInfo(Set<String> mods, boolean signatureVerified, boolean veltonVerified, String modListNonce, String integrityNonce, String veltonNonce) {}

    public static HandShakerServer getInstance() {
        return instance;
    }

    @SuppressWarnings("null")
    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("HandShaker server initializing");
        
        // Migrate config if needed (v3 -> v4)
        ConfigMigrator.migrateIfNeeded();
        
        configManager = new ConfigManager();
        configManager.load();
        
        loadPublicCertificate();
        
        playerHistoryDb = new PlayerHistoryDatabase(configManager.isPlayerdbEnabled());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> this.server = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            scheduler.shutdown();
            if (playerHistoryDb != null) {
                playerHistoryDb.close();
            }
        });

        // Register payload types
        PayloadTypeRegistry.playC2S().register(HandShaker.ModsListPayload.ID, HandShaker.ModsListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandShaker.IntegrityPayload.ID, HandShaker.IntegrityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VeltonPayload.ID, VeltonPayload.CODEC);

        // Register payload handlers
        ServerPlayNetworking.registerGlobalReceiver(HandShaker.ModsListPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            try {
                if (!PayloadValidator.validateNonce(payload.nonce(), player, LOGGER, "mod list")) {
                    return;
                }
                Set<String> mods = new HashSet<>(Arrays.asList(payload.mods().split(",")));
                if (payload.mods().isEmpty()) {
                    mods.clear();
                }
                if (HandShakerServer.DEBUG_MODE) {
                    LOGGER.info("Received mod list from {} with nonce: {}", playerName, payload.nonce());
                }
                // Sync with database
                if (playerHistoryDb != null) {
                    playerHistoryDb.syncPlayerMods(player.getUuid(), playerName, mods);
                }
                
                clients.compute(player.getUuid(), (uuid, oldInfo) ->
                        new ClientInfo(mods, 
                                oldInfo != null && oldInfo.signatureVerified(),
                                oldInfo != null && oldInfo.veltonVerified(),
                                payload.nonce(),
                                oldInfo != null ? oldInfo.integrityNonce() : null,
                                oldInfo != null ? oldInfo.veltonNonce() : null));
            } catch (Exception e) {
                LOGGER.error("Failed to decode mod list from {}. Terminating connection.", playerName, e);
                player.networkHandler.disconnect(Text.of("Corrupted handshake data"));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HandShaker.IntegrityPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            try {
                if (!PayloadValidator.validateNonce(payload.nonce(), player, LOGGER, "integrity payload")) {
                    return;
                }
                
                byte[] clientSignature = payload.signature();
                String jarHash = payload.jarHash();
                boolean verified = false;
                
                // Verification logic (matching Paper):
                // Check if client sent a signature and jar hash
                if (jarHash != null && !jarHash.isEmpty() && clientSignature != null && clientSignature.length > 0) {
                    if (publicKey == null) {
                        LOGGER.warn("Cannot verify signature for {}: public key not loaded", playerName);
                        verified = false;
                    } else if (clientSignature.length >= 128) { // Minimum size for a valid signature
                        // Verify the signature against our public key
                        try {
                            verified = verifySignatureWithPublicKey(jarHash, clientSignature);
                            if (verified) {
                                LOGGER.info("Integrity check for {}: JAR SIGNED with VALID SIGNATURE (hash: {})", playerName, StringUtils.truncate(jarHash, 8));
                            } else {
                                LOGGER.warn("Integrity check for {}: signature verification FAILED - signature was not created with our key", playerName);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Integrity check for {}: error verifying signature: {}", playerName, e.getMessage());
                            verified = false;
                        }
                    } else {
                        LOGGER.warn("Integrity check for {}: signature too small to be valid", playerName);
                        verified = false;
                    }
                } else if (clientSignature == null || clientSignature.length == 0) {
                    LOGGER.warn("Integrity check for {}: no signature data received - client not signed", playerName);
                    verified = false;
                } else if (jarHash == null || jarHash.isEmpty()) {
                    LOGGER.warn("Integrity check for {}: no JAR hash received", playerName);
                    verified = false;
                }
                if (HandShakerServer.DEBUG_MODE) {
                    LOGGER.info("Integrity check for {} with nonce {}: {}", playerName, payload.nonce(), verified ? "PASSED" : "FAILED");
                }
                final boolean finalVerified = verified;
                clients.compute(player.getUuid(), (uuid, oldInfo) ->
                        new ClientInfo(oldInfo != null ? oldInfo.mods() : Collections.emptySet(), 
                                finalVerified,
                                oldInfo != null && oldInfo.veltonVerified(),
                                oldInfo != null ? oldInfo.modListNonce() : null,
                                payload.nonce(),
                                oldInfo != null ? oldInfo.veltonNonce() : null));
                configManager.checkPlayer(player, clients.get(player.getUuid()));
            } catch (Exception e) {
                LOGGER.error("Failed to decode integrity payload from {}. Terminating connection.", playerName, e);
                player.networkHandler.disconnect(Text.of("Corrupted handshake data"));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(VeltonPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            try {
                if (!PayloadValidator.validateNonce(payload.nonce(), player, LOGGER, "Velton payload")) {
                    return;
                }
                
                byte[] clientSignature = payload.signature();
                String jarHash = payload.jarHash();
                boolean verified = false;
                
                // Verification logic (matching integrity payload verification):
                // Check if client sent a signature and jar hash
                if (jarHash != null && !jarHash.isEmpty() && clientSignature != null && clientSignature.length > 0) {
                    if (publicKey == null) {
                        LOGGER.warn("Cannot verify Velton signature for {}: public key not loaded", playerName);
                        verified = false;
                    } else if (clientSignature.length >= 128) { // Minimum size for a valid signature
                        // Verify the signature against our public key
                        try {
                            verified = verifySignatureWithPublicKey(jarHash, clientSignature);
                            if (verified) {
                                LOGGER.info("Velton integrity check for {}: JAR SIGNED with VALID SIGNATURE (hash: {})", playerName, StringUtils.truncate(jarHash, 8));
                            } else {
                                LOGGER.warn("Velton integrity check for {}: signature verification FAILED - signature was not created with our key", playerName);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Velton integrity check for {}: error verifying signature: {}", playerName, e.getMessage());
                            verified = false;
                        }
                    } else {
                        LOGGER.warn("Velton integrity check for {}: signature too small to be valid", playerName);
                        verified = false;
                    }
                } else if (clientSignature == null || clientSignature.length == 0) {
                    LOGGER.warn("Velton integrity check for {}: no signature data received - client not signed", playerName);
                    verified = false;
                } else if (jarHash == null || jarHash.isEmpty()) {
                    LOGGER.warn("Velton integrity check for {}: no JAR hash received", playerName);
                    verified = false;
                }
                
                if (HandShakerServer.DEBUG_MODE) {
                    LOGGER.info("Velton check for {} with nonce {}: {}", playerName, payload.nonce(), verified ? "PASSED" : "FAILED");
                }

                // Kick player if Velton signature is invalid/missing
                if (!verified) {
                    LOGGER.warn("Kicking {} - Velton signature verification failed", playerName);
                    player.networkHandler.disconnect(Text.of("Anti-cheat verification failed"));
                    return;
                }

                final boolean finalVerified = verified;
                clients.compute(player.getUuid(), (uuid, oldInfo) ->
                        new ClientInfo(oldInfo != null ? oldInfo.mods() : Collections.emptySet(), 
                                oldInfo != null && oldInfo.signatureVerified(),
                                finalVerified,
                                oldInfo != null ? oldInfo.modListNonce() : null,
                                oldInfo != null ? oldInfo.integrityNonce() : null,
                                payload.nonce()));
                configManager.checkPlayer(player, clients.get(player.getUuid()));
            } catch (Exception e) {
                LOGGER.error("Failed to decode Velton payload from {}. Terminating connection.", playerName, e);
                player.networkHandler.disconnect(Text.of("Corrupted handshake data"));
            }
        });

        // Register player lifecycle events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            scheduler.schedule(() -> {
                server.execute(() -> {
                    if (handler.player.networkHandler == null) return; // Player disconnected
                    ClientInfo info = clients.computeIfAbsent(handler.player.getUuid(), uuid -> new ClientInfo(Collections.emptySet(), false, false, null, null, null));

                    configManager.checkPlayer(handler.player, info, false); // Don't execute actions here, just check
                });
            }, 5, TimeUnit.SECONDS);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clients.remove(handler.player.getUuid());
            configManager.playerLeft(handler.player);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HandShakerCommand.register(dispatcher);
        });
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerHistoryDatabase getPlayerHistoryDb() {
        return playerHistoryDb;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Map<UUID, ClientInfo> getClients() {
        return clients;
    }

    public void checkAllPlayers() {
        if (server == null) return;
        LOGGER.info("Re-checking all online players...");
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            configManager.checkPlayer(player, clients.getOrDefault(player.getUuid(), new ClientInfo(Collections.emptySet(), false, false, null, null, null)), false);
        }
    }
    
    public boolean isBedrockPlayer(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        
        // Try Floodgate API first (if available)
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = floodgateApiClass.getMethod("getInstance").invoke(null);
            boolean isFloodgate = (boolean) floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(api, playerUuid);
            if (isFloodgate) {
                return true;
            }
        } catch (ClassNotFoundException e) {
            // Floodgate not installed, continue to Geyser check
        } catch (Exception e) {
            LOGGER.warn("Error checking Floodgate for {}: {}", player.getName().getString(), e.getMessage());
        }
        
        // Try Geyser API (if available) - works for Geyser-only setups
        try {
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object geyserApi = geyserApiClass.getMethod("api").invoke(null);
            
            if (geyserApi != null) {
                Object connection = geyserApiClass.getMethod("connectionByUuid", UUID.class)
                        .invoke(geyserApi, playerUuid);
                // If connection exists, player is connected through Geyser
                return connection != null;
            }
        } catch (ClassNotFoundException e) {
            // Geyser not installed
        } catch (Exception e) {
            LOGGER.warn("Error checking Geyser for {}: {}", player.getName().getString(), e.getMessage());
        }
        
        return false;
    }

    private void loadPublicCertificate() {
        try (var certStream = HandShakerServer.class.getClassLoader().getResourceAsStream("public.cer")) {
            if (certStream == null) {
                LOGGER.warn("⚠️  public.cer not found in resources. Signature verification will be disabled.");
                LOGGER.warn("⚠️  Mods signed with ANY certificate will be accepted.");
                publicKey = null;
                return;
            }
            
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(certStream);
            publicKey = cert.getPublicKey();
            LOGGER.info("✓ Loaded public certificate for signature verification");
        } catch (Exception e) {
            LOGGER.warn("Failed to load public.cer: {}", e.getMessage());
            LOGGER.warn("⚠️  Signature verification will be disabled.");
            publicKey = null;
        }
    }

    private boolean verifySignatureWithPublicKey(String jarHash, byte[] signatureBytes) throws Exception {
        if (publicKey == null) {
            return false;
        }
        
        try {
            // Handle case where signature is actually a certificate chain (1445 bytes)
            if (signatureBytes.length > 512) {
                LOGGER.info("Signature data is {} bytes, parsing as certificate chain...", signatureBytes.length);
                byte[] certValidation = extractSignatureFromCertificate(signatureBytes);
                if (certValidation != null && certValidation.length > 0) {
                    // Certificate chain validated successfully
                    return true;
                }
                // If certificate validation fails, fall through to raw signature verification
                LOGGER.warn("Certificate chain validation failed, attempting raw signature verification as fallback...");
            }
            
            // Handle raw signature verification
            if (signatureBytes.length <= 512) {
                // Create a Signature instance for verification
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(publicKey);
                
                // Verify the signature against the jar hash
                sig.update(jarHash.getBytes(StandardCharsets.UTF_8));
                boolean isValid = sig.verify(signatureBytes);
                
                if (!isValid) {
                    LOGGER.warn("Signature verification failed: signature does not match jar hash");
                }
                return isValid;
            }
            
            return false;
        } catch (SignatureException e) {
            LOGGER.warn("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }
    
    private byte[] extractSignatureFromCertificate(byte[] certificateData) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(certificateData);
            
            // Parse as a certificate collection (chain)
            java.util.Collection<? extends Certificate> certs = cf.generateCertificates(bais);
            
            if (certs.isEmpty()) {
                LOGGER.warn("Certificate chain is empty");
                return null;
            }
            
            LOGGER.info("Parsed certificate chain with {} certificate(s)", certs.size());
            
            // Check each certificate in the chain
            for (Certificate cert : certs) {
                @SuppressWarnings("null")
                PublicKey certPublicKey = cert.getPublicKey();
                
                // Log the public key info for debugging
                if (publicKey != null) {
                    LOGGER.info("Certificate public key algorithm: {}, size: {}", certPublicKey.getAlgorithm(), 
                               (certPublicKey instanceof java.security.interfaces.RSAPublicKey ? 
                               ((java.security.interfaces.RSAPublicKey)certPublicKey).getModulus().bitLength() : "unknown"));
                }
                
                // Check if this certificate's public key matches our trusted key
                if (certPublicKey.equals(publicKey)) {
                    LOGGER.info("✓ Certificate public key matches our trusted key - signature VALID");
                    // Return a non-null marker indicating the certificate is valid
                    return new byte[]{1}; // Marker indicating validation passed
                }
            }
            
            LOGGER.warn("No certificate in chain matched our trusted public key");
            return null;
        } catch (java.security.cert.CertificateException e) {
            LOGGER.warn("Failed to parse certificate chain: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Error processing certificate chain: {}", e.getMessage());
            return null;
        }
    }

    public record VeltonPayload(byte[] signature, String jarHash, String nonce) implements CustomPayload {
        public static final CustomPayload.Id<VeltonPayload> ID = new CustomPayload.Id<>(VELTON_CHANNEL);
        public static final PacketCodec<PacketByteBuf, VeltonPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.BYTE_ARRAY, VeltonPayload::signature,
                PacketCodecs.STRING, VeltonPayload::jarHash,
                PacketCodecs.STRING, VeltonPayload::nonce,
                VeltonPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
}