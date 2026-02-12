package me.mklv.handshaker.neoforge.server;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.protocols.CertLoader;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.HashUtils;
import me.mklv.handshaker.common.utils.SignatureVerifier;
import me.mklv.handshaker.common.configs.StandardMessages;
import me.mklv.handshaker.neoforge.NetworkSetup;
import me.mklv.handshaker.common.configs.ConfigMigrator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(HandShakerServerMod.MOD_ID)
public class HandShakerServerMod {
    public static final String MOD_ID = "hand_shaker";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static HandShakerServerMod instance;

    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private final Set<String> usedNonces = ConcurrentHashMap.newKeySet(); // Track used nonces for replay prevention
    private ConfigManager blacklistConfig;
    private PlayerHistoryDatabase playerHistoryDb;
    private MinecraftServer server;
    private PublicKey publicKey;
    private SignatureVerifier signatureVerifier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public HandShakerServerMod(IEventBus modEventBus) {
        instance = this;
        LOGGER.info("HandShaker server initializing");

        // Run migration if needed (v3 -> v4)
        ConfigMigrator.migrateIfNeeded(
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get(),
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

        blacklistConfig = new ConfigManager();
        blacklistConfig.load();
        
        loadPublicCertificate();

        // Initialize player history database with NeoForge logger adapter
        playerHistoryDb = new PlayerHistoryDatabase(FMLPaths.CONFIGDIR.get().toFile(), new NeoForgeLoggerAdapter(LOGGER));

        // Register payloads once via centralized NetworkSetup
        modEventBus.addListener(NetworkSetup::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
    }

    public static HandShakerServerMod getInstance() {
        return instance;
    }

    public void handleModsList(final ModsListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                if (payload.nonce() == null || payload.nonce().isEmpty()) {
                    LOGGER.warn("Received mod list from {} with invalid/missing nonce. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                        StandardMessages.HANDSHAKE_MISSING_NONCE)));
                    return;
                }
                
                // Check for replay attack (nonce already used)
                if (usedNonces.contains(payload.nonce())) {
                    LOGGER.warn("Received mod list from {} with replay nonce. Kicking.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_REPLAY,
                        StandardMessages.HANDSHAKE_REPLAY)));
                    return;
                }
                
                // Verify hash matches payload
                String calculatedHash = HashUtils.sha256Hex(payload.mods());
                if (!calculatedHash.equals(payload.modListHash())) {
                    LOGGER.warn("Received mod list from {} with mismatched hash. Expected {} but got {}", 
                        player.getName().getString(), calculatedHash, payload.modListHash());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_HASH_MISMATCH,
                        StandardMessages.HANDSHAKE_HASH_MISMATCH)));
                    return;
                }
                
                usedNonces.add(payload.nonce());
                
                if (payload.mods() == null || payload.mods().isEmpty()) {
                    LOGGER.warn("Received empty mod list from {}. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_EMPTY_MOD_LIST,
                        StandardMessages.HANDSHAKE_EMPTY_MOD_LIST)));
                    return;
                }
                Set<String> mods = new HashSet<>(Arrays.asList(payload.mods().split(",")));
                LOGGER.info("Received mod list from {} with nonce: {}", player.getName().getString(), payload.nonce());

                if (playerHistoryDb != null) {
                    playerHistoryDb.syncPlayerMods(player.getUUID(), player.getName().getString(), mods);
                }

                clients.compute(player.getUUID(), (uuid, oldInfo) ->
                        new ClientInfo(mods,
                                oldInfo != null && oldInfo.signatureVerified(),
                                oldInfo != null && oldInfo.veltonVerified(),
                                payload.nonce(),
                                oldInfo != null ? oldInfo.integrityNonce() : null,
                                oldInfo != null ? oldInfo.veltonNonce() : null));
            } catch (Exception e) {
                LOGGER.error("Failed to decode mod list from {}. Terminating connection.", player.getName().getString(), e);
                player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });
    }

    public void handleIntegrity(final IntegrityPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                if (payload.nonce() == null || payload.nonce().isEmpty()) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing nonce. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                        StandardMessages.HANDSHAKE_MISSING_NONCE)));
                    return;
                }
                
                byte[] clientSignature = payload.signature();
                String jarHash = payload.jarHash();
                if (clientSignature == null || clientSignature.length == 0) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing signature. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_SIGNATURE,
                        StandardMessages.HANDSHAKE_MISSING_SIGNATURE)));
                    return;
                }
                if (clientSignature.length == 1) {
                    LOGGER.warn("Received legacy integrity payload from {}. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_OUTDATED_CLIENT,
                        StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE)));
                    return;
                }
                if (jarHash == null || jarHash.isEmpty()) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing jar hash. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH,
                        StandardMessages.HANDSHAKE_MISSING_JAR_HASH)));
                    return;
                }
                if (clientSignature == null || clientSignature.length == 0) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing signature. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(StandardMessages.HANDSHAKE_MISSING_SIGNATURE));
                    return;
                }
                if (jarHash == null || jarHash.isEmpty()) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing jar hash. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(StandardMessages.HANDSHAKE_MISSING_JAR_HASH));
                    return;
                }
                boolean verified = false;
                
                // Verification logic (matching Fabric/Paper):
                // Check if client sent a signature and jar hash
                if (jarHash != null && !jarHash.isEmpty() && clientSignature != null && clientSignature.length > 0) {
                    // Check if this is a 1-byte verification flag from the client (backward compatibility)
                    // or an actual RSA signature for server-side verification
                    if (clientSignature.length == 1) {
                        // Client sent a local verification flag: {1} = verified, {0} = not verified
                        boolean signatureVerified = clientSignature[0] == 1;
                        if (signatureVerified) {
                            LOGGER.info("Integrity check for {}: JAR signature VERIFIED locally by client (hash: {})", 
                                player.getName().getString(), jarHash.substring(0, Math.min(8, jarHash.length())));
                            verified = true;
                        } else {
                            LOGGER.warn("Integrity check for {}: client reported signature NOT verified", player.getName().getString());
                            verified = false;
                        }
                    } else {
                        // Client sent an actual RSA signature for server-side verification
                        if (publicKey == null) {
                            LOGGER.warn("Cannot verify signature for {}: public key not loaded", player.getName().getString());
                            verified = false;
                        } else {
                            try {
                                verified = verifySignatureWithPublicKey(jarHash, clientSignature);
                                if (verified) {
                                    LOGGER.info("Integrity check for {}: JAR SIGNED with VALID SIGNATURE (hash: {})", player.getName().getString(), jarHash.substring(0, Math.min(8, jarHash.length())));
                                } else {
                                    LOGGER.warn("Integrity check for {}: signature verification FAILED - signature was not created with our key", player.getName().getString());
                                }
                            } catch (Exception e) {
                                LOGGER.warn("Integrity check for {}: error verifying signature: {}", player.getName().getString(), e.getMessage());
                                verified = false;
                            }
                        }
                    }
                } else if (clientSignature == null || clientSignature.length == 0) {
                    LOGGER.warn("Integrity check for {}: no signature data received - client not signed", player.getName().getString());
                    verified = false;
                } else if (jarHash == null || jarHash.isEmpty()) {
                    LOGGER.warn("Integrity check for {}: no JAR hash received", player.getName().getString());
                    verified = false;
                }
                
                LOGGER.info("Integrity check for {} with nonce {}: {}", player.getName().getString(), payload.nonce(), verified ? "PASSED" : "FAILED");

                final boolean finalVerified = verified;
                clients.compute(player.getUUID(), (uuid, oldInfo) ->
                        new ClientInfo(oldInfo != null ? oldInfo.mods() : Collections.emptySet(),
                                finalVerified,
                                oldInfo != null && oldInfo.veltonVerified(),
                                oldInfo != null ? oldInfo.modListNonce() : null,
                                payload.nonce(),
                                oldInfo != null ? oldInfo.veltonNonce() : null));
                blacklistConfig.checkPlayer(player, clients.get(player.getUUID()));
            } catch (Exception e) {
                LOGGER.error("Failed to decode integrity payload from {}. Terminating connection.", player.getName().getString(), e);
                player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });
    }

    public void handleVelton(final VeltonPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                if (payload.nonce() == null || payload.nonce().isEmpty()) {
                    LOGGER.warn("Received Velton payload from {} with invalid/missing nonce. Rejecting.", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                        StandardMessages.HANDSHAKE_MISSING_NONCE)));
                    return;
                }
                String signatureHash = payload.signatureHash();
                boolean verified = signatureHash != null && !signatureHash.isEmpty();

                LOGGER.info("Velton check for {} with nonce {}: {}", player.getName().getString(), payload.nonce(), verified ? "PASSED" : "FAILED");

                if (!verified) {
                    LOGGER.warn("Kicking {} - Velton signature verification failed", player.getName().getString());
                    player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                        StandardMessages.KEY_VELTON_FAILED,
                        StandardMessages.VELTON_VERIFICATION_FAILED)));
                    return;
                }

                final boolean finalVerified = verified;
                clients.compute(player.getUUID(), (uuid, oldInfo) ->
                        new ClientInfo(oldInfo != null ? oldInfo.mods() : Collections.emptySet(),
                                oldInfo != null && oldInfo.signatureVerified(),
                                finalVerified,
                                oldInfo != null ? oldInfo.modListNonce() : null,
                                oldInfo != null ? oldInfo.integrityNonce() : null,
                                payload.nonce()));
                blacklistConfig.checkPlayer(player, clients.get(player.getUUID()));
            } catch (Exception e) {
                LOGGER.error("Failed to decode Velton payload from {}. Terminating connection.", player.getName().getString(), e);
                player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        this.server = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        scheduler.shutdown();
        if (playerHistoryDb != null) {
            playerHistoryDb.close();
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        scheduler.schedule(() -> {
            if (server == null) return;
            server.execute(() -> {
                if (player.connection == null) return;
                ClientInfo info = clients.computeIfAbsent(player.getUUID(), uuid -> new ClientInfo(Collections.emptySet(), false, false, null, null, null));
                blacklistConfig.checkPlayer(player, info);
            });
        }, blacklistConfig.getHandshakeTimeoutSeconds(), TimeUnit.SECONDS);
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        clients.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        HandShakerCommand.register(event.getDispatcher());
    }

    public ConfigManager getBlacklistConfig() {
        return blacklistConfig;
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

    public Set<String> getClientMods(UUID uuid) {
        ClientInfo info = clients.get(uuid);
        return info != null ? info.mods() : null;
    }

    public void checkAllPlayers() {
        if (server == null) return;
        LOGGER.info("Re-checking all online players...");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            blacklistConfig.checkPlayer(player, clients.getOrDefault(player.getUUID(), new ClientInfo(Collections.emptySet(), false, false, null, null, null)));
        }
    }

    public boolean isBedrockPlayer(ServerPlayer player) {
        return BedrockPlayer.isBedrockPlayer(player.getUUID(), player.getName().getString(), new BedrockPlayer.LogSink() {
            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }
        });
    }

    public record ModsListPayload(String mods, String modListHash, String nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ModsListPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("hand-shaker", "mods"));
        public static final StreamCodec<ByteBuf, ModsListPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ModsListPayload::mods,
                ByteBufCodecs.STRING_UTF8, ModsListPayload::modListHash,
                ByteBufCodecs.STRING_UTF8, ModsListPayload::nonce,
                ModsListPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private void loadPublicCertificate() {
        publicKey = CertLoader.loadPublicKey(HandShakerServerMod.class.getClassLoader(), "public.cer", new CertLoader.LogSink() {
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

    public record IntegrityPayload(byte[] signature, String jarHash, String nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<IntegrityPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("hand-shaker", "integrity"));
        public static final StreamCodec<ByteBuf, IntegrityPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE_ARRAY, IntegrityPayload::signature,
                ByteBufCodecs.STRING_UTF8, IntegrityPayload::jarHash,
                ByteBufCodecs.STRING_UTF8, IntegrityPayload::nonce,
                IntegrityPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record VeltonPayload(String signatureHash, String nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<VeltonPayload> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("velton", "signature"));
        public static final StreamCodec<ByteBuf, VeltonPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, VeltonPayload::signatureHash,
                ByteBufCodecs.STRING_UTF8, VeltonPayload::nonce,
                VeltonPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static class NeoForgeLoggerAdapter implements PlayerHistoryDatabase.Logger {
        private final Logger logger;

        NeoForgeLoggerAdapter(Logger logger) {
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
}
