package me.mklv.handshaker.fabric.server;

import me.mklv.handshaker.fabric.HandShaker;
import me.mklv.handshaker.fabric.server.configs.ConfigManager;
import me.mklv.handshaker.common.configs.ConfigMigrator;
import me.mklv.handshaker.common.configs.StandardMessages;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.protocols.CertLoader;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.SignatureVerifier;
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

import java.nio.file.Path;
import java.security.PublicKey;
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
    private SignatureVerifier signatureVerifier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static HandShakerServer getInstance() {
        return instance;
    }

    @SuppressWarnings("null")
    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("HandShaker server initializing");
        
        // Migrate config if needed (v3 -> v4)
        ConfigMigrator.migrateIfNeeded(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir(),
            new ConfigMigrator.Logger() {
                @Override
                public void info(String message) {
                    LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    LOGGER.warn(message);
                }

                @Override
                public void error(String message, Throwable error) {
                    LOGGER.error(message, error);
                }
            }
        );
        
        configManager = new ConfigManager();
        configManager.load();
        
        loadPublicCertificate();
        
        // Initialize player history database if enabled
        if (configManager.isPlayerdbEnabled()) {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            playerHistoryDb = new PlayerHistoryDatabase(configDir.toFile(), new FabricLoggerAdapter(LOGGER));
        }

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
                if (!validateNonce(payload.nonce(), player, LOGGER, "mod list")) {
                    return;
                }
                if (payload.mods() == null || payload.mods().isEmpty()) {
                    LOGGER.warn("Received empty mod list from {}. Rejecting.", playerName);
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_EMPTY_MOD_LIST,
                        StandardMessages.HANDSHAKE_EMPTY_MOD_LIST)));
                    return;
                }
                Set<String> mods = new HashSet<>(Arrays.asList(payload.mods().split(",")));
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
                player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HandShaker.IntegrityPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            try {
                if (!validateNonce(payload.nonce(), player, LOGGER, "integrity payload")) {
                    return;
                }
                
                byte[] clientSignature = payload.signature();
                String jarHash = payload.jarHash();
                if (clientSignature == null || clientSignature.length == 0) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing signature. Rejecting.", playerName);
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_SIGNATURE,
                        StandardMessages.HANDSHAKE_MISSING_SIGNATURE)));
                    return;
                }
                if (clientSignature.length == 1) {
                    LOGGER.warn("Received legacy integrity payload from {}. Rejecting.", playerName);
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_OUTDATED_CLIENT,
                        StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE)));
                    return;
                }
                if (jarHash == null || jarHash.isEmpty()) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing jar hash. Rejecting.", playerName);
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH,
                        StandardMessages.HANDSHAKE_MISSING_JAR_HASH)));
                    return;
                }
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
                player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(VeltonPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            try {
                if (!validateNonce(payload.nonce(), player, LOGGER, "Velton payload")) {
                    return;
                }
                
                byte[] clientSignature = payload.signature();
                String jarHash = payload.jarHash();
                if (clientSignature == null || clientSignature.length == 0) {
                    LOGGER.warn("Received Velton payload from {} with invalid/missing signature. Rejecting.", playerName);
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_SIGNATURE,
                        StandardMessages.HANDSHAKE_MISSING_SIGNATURE)));
                    return;
                }
                if (clientSignature.length == 1) {
                    LOGGER.warn("Received legacy Velton payload from {}. Rejecting.", playerName);
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_OUTDATED_CLIENT,
                        StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE)));
                    return;
                }
                if (jarHash == null || jarHash.isEmpty()) {
                    LOGGER.warn("Received Velton payload from {} with invalid/missing jar hash. Rejecting.", playerName);
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH,
                        StandardMessages.HANDSHAKE_MISSING_JAR_HASH)));
                    return;
                }
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
                    player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                        StandardMessages.KEY_VELTON_FAILED,
                        StandardMessages.VELTON_VERIFICATION_FAILED)));
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
                player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
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
            }, configManager.getHandshakeTimeoutSeconds(), TimeUnit.SECONDS);
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
        return BedrockPlayer.isBedrockPlayer(player.getUuid(), player.getName().getString(), new BedrockPlayer.LogSink() {
            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }
        });
    }

    private void loadPublicCertificate() {
        publicKey = CertLoader.loadPublicKey(HandShakerServer.class.getClassLoader(), "public.cer", new CertLoader.LogSink() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }
        });

        if (publicKey != null) {
            signatureVerifier = new SignatureVerifier(publicKey, new SignatureVerifier.LogSink() {
                @Override
                public void info(String message) {
                    LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    LOGGER.warn(message);
                }
            });
        } else {
            signatureVerifier = null;
        }
    }

    private boolean verifySignatureWithPublicKey(String jarHash, byte[] signatureBytes) throws Exception {
        if (signatureVerifier == null || !signatureVerifier.isKeyLoaded()) {
            return false;
        }
        return signatureVerifier.verifySignature(jarHash, signatureBytes);
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

    private static class FabricLoggerAdapter implements PlayerHistoryDatabase.Logger {
        private final Logger logger;

        FabricLoggerAdapter(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void info(String message, Object... args) {
            logger.info(message, args);
        }

        @Override
        public void warn(String message, Object... args) {
            logger.warn(message, args);
        }

        @Override
        public void error(String message, Throwable e) {
            logger.error(message, e);
        }

        @Override
        public void debug(String message) {
            logger.debug(message);
        }
    }
    public class StringUtils {

        public static String truncate(String str, int maxLength) {
            if (str == null) return "";
            return str.substring(0, Math.min(maxLength, str.length()));
        }

        public static String safePlayerName(String playerName) {
            return playerName == null || playerName.isEmpty() ? "Unknown" : playerName;
        }

        public static boolean isNullOrEmpty(String str) {
            return str == null || str.isEmpty();
        }
    }
    private boolean validateNonce(String nonce, ServerPlayerEntity player, Logger logger, String payloadType) {
        if (nonce == null || nonce.isEmpty()) {
            logger.warn("Received {} from {} with invalid/missing nonce. Rejecting.", payloadType, player.getName().getString());
            player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                StandardMessages.HANDSHAKE_MISSING_NONCE)));
            return false;
        }
        return true;
    }
}