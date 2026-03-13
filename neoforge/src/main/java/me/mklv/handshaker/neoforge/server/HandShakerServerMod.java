package me.mklv.handshaker.neoforge.server;

import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import me.mklv.handlib.network.PayloadTypeCompat;
import me.mklv.handshaker.neoforge.NetworkSetup;
import me.mklv.handshaker.common.api.local.ApiDataProvider;
import me.mklv.handshaker.common.api.local.ApiServerConfig;
import me.mklv.handshaker.common.api.local.LocalRestApiServer;
import me.mklv.handshaker.common.api.discord.WebhookDispatcher;
import me.mklv.handshaker.common.configs.ConfigMigration.ConfigMigrator;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;
import me.mklv.handshaker.common.server.ServerApiProviderFactory;
import me.mklv.handshaker.common.server.ServerSecurityWebhookSupport;
import me.mklv.handshaker.common.database.H2PlayerHistoryDatabase;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.protocols.LegacyVersion;
import me.mklv.handshaker.common.protocols.PayloadValidation;
import me.mklv.handshaker.common.protocols.PayloadValidation.PayloadValidationCallbacks;
import me.mklv.handshaker.common.protocols.PayloadValidation.ValidationResult;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.SignatureVerifier;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(HandShakerServerMod.MOD_ID)
public class HandShakerServerMod {
    public static final String MOD_ID = "hand-shaker";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static HandShakerServerMod instance;

    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private ConfigManager blacklistConfig;
    private PlayerHistoryDatabase playerHistoryDb;
    private PayloadValidation payloadValidator;
    private MinecraftServer server;

    private SignatureVerifier signatureVerifier;
    private LocalRestApiServer localRestApiServer;
    private WebhookDispatcher webhookDispatcher;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public HandShakerServerMod(IEventBus modEventBus) {
        instance = this;
        LOGGER.info("HandShaker server initializing");

        if (!ModList.get().isLoaded("handlib")) {
            throw new RuntimeException("handlib is required on the server! Download it from https://modrinth.com/mod/handlib");
        }

        Path configDir = FMLPaths.CONFIGDIR.get();
        ConfigMigrator.migrateIfNeeded(
            configDir,
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

        LegacyVersion.initializeTrustedHybridHashes(getClass(), new LegacyVersion.LogSink() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }
        }, isDebugMode());

        loadPublicCertificate();

        if (blacklistConfig.isPlayerdbEnabled()) {
            PlayerHistoryDatabase.DatabaseOptions options = PlayerHistoryDatabase.DatabaseOptions.of(
                blacklistConfig.getDatabasePoolSize(),
                blacklistConfig.getDatabaseIdleTimeoutMs(),
                blacklistConfig.getDatabaseMaxLifetimeMs()
            );
            playerHistoryDb = new H2PlayerHistoryDatabase(configDir.toFile(), LoggerAdapter.fromLoaderDatabaseLogger(LOGGER), options);
        }

        startLocalRestApiIfEnabled();
        startWebhookIfEnabled();

        payloadValidator = new PayloadValidation(
            new PayloadValidationCallbacks() {
                @Override
                public String getMessageOrDefault(String key, String defaultMessage) {
                    return blacklistConfig.getMessageOrDefault(key, defaultMessage);
                }

                @Override
                public boolean isModernCompatibilityEnabled() {
                    return blacklistConfig.isModernCompatibilityEnabled();
                }

                @Override
                public boolean isHybridCompatibilityEnabled() {
                    return blacklistConfig.isHybridCompatibilityEnabled();
                }

                @Override
                public boolean isLegacyCompatibilityEnabled() {
                    return blacklistConfig.isLegacyCompatibilityEnabled();
                }

                @Override
                public boolean isUnsignedCompatibilityEnabled() {
                    return blacklistConfig.isUnsignedCompatibilityEnabled();
                }

                @Override
                public boolean isRateLimitEnabled() {
                    return blacklistConfig.getRateLimitPerMinute() > 0;
                }

                @Override
                public int getRateLimitPerMinute() {
                    return blacklistConfig.getRateLimitPerMinute();
                }

                @Override
                public boolean isPayloadCompressionEnabled() {
                    return blacklistConfig.isPayloadCompressionEnabled();
                }

                @Override
                public void logInfo(String format, Object... args) {
                    LOGGER.info(String.format(format, args));
                }

                @Override
                public void logWarning(String format, Object... args) {
                    LOGGER.warn(String.format(format, args));
                }

                @Override
                public void syncPlayerMods(UUID playerId, String playerName, Set<String> mods) {
                    if (playerHistoryDb != null) {
                        if (blacklistConfig.isAsyncDatabaseOperations() && blacklistConfig.isRuntimeCache()) {
                            scheduler.submit(() -> {
                                try {
                                    playerHistoryDb.syncPlayerMods(playerId, playerName, mods);
                                } catch (Exception e) {
                                    LOGGER.warn("Failed to async-sync player mods: {}", e.getMessage());
                                }
                            });
                        } else {
                            playerHistoryDb.syncPlayerMods(playerId, playerName, mods);
                        }
                    }
                }

                @Override
                public void checkPlayer(UUID playerId, String playerName, ClientInfo info) {
                    if (server == null) return;
                    @SuppressWarnings("null")
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        blacklistConfig.checkPlayer(player, info);
                    }
                }
            },
            signatureVerifier,
            clients
        );

        // Register payloads once via centralized NetworkSetup
        modEventBus.addListener(NetworkSetup::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
    }

    public static HandShakerServerMod getInstance() {
        return instance;
    }

    @SuppressWarnings("null")
    public void handleModsList(final ModsListPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                ValidationResult result = payloadValidator.validateModList(
                    player.getUUID(), player.getName().getString(), payload.mods(), payload.modListHash(), payload.nonce());
                
                if (!result.success) {
                    player.connection.disconnect(Component.literal(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process mod list from {}. Terminating connection.", player.getName().getString(), e);
                player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });
    }

    @SuppressWarnings("null")
    public void handleIntegrity(final IntegrityPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                ValidationResult result = payloadValidator.validateIntegrity(
                    player.getUUID(), player.getName().getString(), payload.signature(), payload.jarHash(), payload.nonce());
                
                if (!result.success) {
                    player.connection.disconnect(Component.literal(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process integrity payload from {}. Terminating connection.", player.getName().getString(), e);
                player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });
    }

    @SuppressWarnings("null")
    public void handleVelton(final VeltonPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                ValidationResult result = payloadValidator.validateVelton(
                    player.getUUID(), player.getName().getString(), payload.signatureHash(), payload.nonce());
                
                if (!result.success) {
                    player.connection.disconnect(Component.literal(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process Velton payload from {}. Terminating connection.", player.getName().getString(), e);
                player.connection.disconnect(Component.literal(blacklistConfig.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        this.server = event.getServer();
        scheduler.scheduleAtFixedRate(() -> payloadValidator.cleanupExpiredNoncesNow(), 5, 5, TimeUnit.MINUTES);
        int days = blacklistConfig.getDeleteHistoryDays();
        if (days > 0) {
            scheduler.scheduleAtFixedRate(() -> {
                if (playerHistoryDb != null) {
                    playerHistoryDb.deleteOldHistory(blacklistConfig.getDeleteHistoryDays());
                }
            }, 1, 1, TimeUnit.HOURS);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (webhookDispatcher != null) {
            webhookDispatcher.shutdown();
            webhookDispatcher = null;
        }
        if (localRestApiServer != null) {
            localRestApiServer.stop();
            localRestApiServer = null;
        }
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
        payloadValidator.clearNonceHistory(event.getEntity().getUUID());
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

    public java.util.concurrent.ScheduledExecutorService getScheduler() {
        return scheduler;
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
        public static final CustomPacketPayload.Type<ModsListPayload> TYPE = PayloadTypeCompat.payloadType("hand-shaker", "mods");
        @SuppressWarnings("null")
        public static final StreamCodec<ByteBuf, ModsListPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ModsListPayload::mods,
                ByteBufCodecs.STRING_UTF8, ModsListPayload::modListHash,
                ByteBufCodecs.STRING_UTF8, ModsListPayload::nonce,
                ModsListPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private void loadPublicCertificate() {
        ServerSecurityWebhookSupport.SecurityMaterial security = ServerSecurityWebhookSupport.loadSecurityMaterial(
            HandShakerServerMod.class.getClassLoader(),
            "public.cer",
            isDebugMode(),
            new ServerSecurityWebhookSupport.Logger() {
                @Override
                public void info(String message) {
                    LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    LOGGER.warn(message);
                }
            }
        );
        signatureVerifier = security.signatureVerifier();
    }

    private void startLocalRestApiIfEnabled() {
        if (blacklistConfig == null || !blacklistConfig.isRestApiEnabled()) {
            return;
        }

        ApiServerConfig apiConfig = new ApiServerConfig(true, blacklistConfig.getRestApiPort(), "");
        localRestApiServer = new LocalRestApiServer(apiConfig, createApiProvider(), LOGGER);
        try {
            localRestApiServer.start();
        } catch (IOException e) {
            LOGGER.warn("Failed to start local REST API: {}", e.getMessage());
        }
    }

    private ApiDataProvider createApiProvider() {
        return ServerApiProviderFactory.create(
            playerHistoryDb,
            clients,
            () -> {
                if (server == null) {
                    return Collections.emptyList();
                }
                List<ServerApiProviderFactory.LivePlayer> online = new java.util.ArrayList<>();
                for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                    UUID id = sp.getUUID();
                    String name = sp.getName().getString();
                    online.add(new ServerApiProviderFactory.LivePlayer() {
                        @Override
                        public UUID uuid() {
                            return id;
                        }

                        @Override
                        public String name() {
                            return name;
                        }
                    });
                }
                return online;
            }
        );
    }

    private void startWebhookIfEnabled() {
        webhookDispatcher = ServerSecurityWebhookSupport.createWebhookDispatcher(blacklistConfig, LOGGER);
    }

    public void publishWebhookKick(String playerName, String reason, String mod) {
        ServerSecurityWebhookSupport.publishKick(webhookDispatcher, playerName, reason, mod);
    }

    public void publishWebhookBan(String playerName, String reason, String mod) {
        ServerSecurityWebhookSupport.publishBan(webhookDispatcher, playerName, reason, mod);
    }

    private boolean isDebugMode() {
        return blacklistConfig != null && blacklistConfig.isDebug();
    }

    public record IntegrityPayload(byte[] signature, String jarHash, String nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<IntegrityPayload> TYPE = PayloadTypeCompat.payloadType("hand-shaker", "integrity");
        @SuppressWarnings("null")
        public static final StreamCodec<ByteBuf, IntegrityPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE_ARRAY, IntegrityPayload::signature,
                ByteBufCodecs.STRING_UTF8, IntegrityPayload::jarHash,
                ByteBufCodecs.STRING_UTF8, IntegrityPayload::nonce,
                IntegrityPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record VeltonPayload(String signatureHash, String nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<VeltonPayload> TYPE = PayloadTypeCompat.payloadType("velton", "signature");
        @SuppressWarnings("null")
        public static final StreamCodec<ByteBuf, VeltonPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, VeltonPayload::signatureHash,
                ByteBufCodecs.STRING_UTF8, VeltonPayload::nonce,
                VeltonPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

}
