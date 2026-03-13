package me.mklv.handshaker.fabric.server;

import me.mklv.handlib.network.PayloadTypeCompat;
import me.mklv.handshaker.fabric.HandShaker;
import me.mklv.handshaker.common.api.local.ApiDataProvider;
import me.mklv.handshaker.common.api.local.ApiServerConfig;
import me.mklv.handshaker.common.api.local.LocalRestApiServer;
import me.mklv.handshaker.common.api.discord.WebhookDispatcher;
import me.mklv.handshaker.common.server.ServerApiProviderFactory;
import me.mklv.handshaker.common.server.ServerSecurityWebhookSupport;
import me.mklv.handshaker.common.configs.ConfigMigration.ConfigMigrator;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;
import me.mklv.handshaker.common.database.H2PlayerHistoryDatabase;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.protocols.LegacyVersion;
import me.mklv.handshaker.common.protocols.PayloadValidation;
import me.mklv.handshaker.common.protocols.PayloadValidation.PayloadValidationCallbacks;
import me.mklv.handshaker.common.protocols.PayloadValidation.ValidationResult;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.SignatureVerifier;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Collections;
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
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + "-server");
    private static HandShakerServer instance;
    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private ConfigManager configManager;
    private PlayerHistoryDatabase playerHistoryDb;
    private PayloadValidation payloadValidator;
    private MinecraftServer server;
    private SignatureVerifier signatureVerifier;
    private LocalRestApiServer localRestApiServer;
    private WebhookDispatcher webhookDispatcher;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static HandShakerServer getInstance() {
        return instance;
    }

    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("HandShaker server initializing");

        if (!FabricLoader.getInstance().isModLoaded("handlib")) {
            throw new RuntimeException("handlib is required on the server! Download it from https://modrinth.com/mod/handlib");
        }
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

        DEBUG_MODE = configManager.isDebug();

        LegacyVersion.initializeTrustedHybridHashes(getClass(), new LegacyVersion.LogSink() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }
        }, DEBUG_MODE);
        
        loadPublicCertificate();
        
        // Initialize player history database if enabled
        if (configManager.isPlayerdbEnabled()) {
            Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
            PlayerHistoryDatabase.DatabaseOptions options = PlayerHistoryDatabase.DatabaseOptions.of(
                configManager.getDatabasePoolSize(),
                configManager.getDatabaseIdleTimeoutMs(),
                configManager.getDatabaseMaxLifetimeMs()
            );
            playerHistoryDb = new H2PlayerHistoryDatabase(configDir.toFile(), LoggerAdapter.fromLoaderDatabaseLogger(LOGGER), options);
        }

        startLocalRestApiIfEnabled();
        startWebhookIfEnabled();

        // Initialize unified payload validator with Fabric-specific callbacks
        this.payloadValidator = new PayloadValidation(
            new PayloadValidationCallbacks() {
                @Override
                public String getMessageOrDefault(String key, String defaultMessage) {
                    return configManager.getMessageOrDefault(key, defaultMessage);
                }

                @Override
                public boolean isModernCompatibilityEnabled() {
                    return configManager.isModernCompatibilityEnabled();
                }

                @Override
                public boolean isHybridCompatibilityEnabled() {
                    return configManager.isHybridCompatibilityEnabled();
                }

                @Override
                public boolean isLegacyCompatibilityEnabled() {
                    return configManager.isLegacyCompatibilityEnabled();
                }

                @Override
                public boolean isUnsignedCompatibilityEnabled() {
                    return configManager.isUnsignedCompatibilityEnabled();
                }

                @Override
                public boolean isRateLimitEnabled() {
                    return configManager.getRateLimitPerMinute() > 0;
                }

                @Override
                public int getRateLimitPerMinute() {
                    return configManager.getRateLimitPerMinute();
                }

                @Override
                public boolean isPayloadCompressionEnabled() {
                    return configManager.isPayloadCompressionEnabled();
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
                        if (configManager.isAsyncDatabaseOperations() && configManager.isRuntimeCache()) {
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
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        configManager.checkPlayer(player, info, false);
                    }
                }
            },
            signatureVerifier,
            clients
        );
        payloadValidator.setDebugMode(DEBUG_MODE);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            this.server = server;
            scheduler.scheduleAtFixedRate(() -> payloadValidator.cleanupExpiredNoncesNow(), 5, 5, TimeUnit.MINUTES);
            int days = configManager.getDeleteHistoryDays();
            if (days > 0) {
                scheduler.scheduleAtFixedRate(() -> {
                    if (playerHistoryDb != null) {
                        playerHistoryDb.deleteOldHistory(configManager.getDeleteHistoryDays());
                    }
                }, 1, 1, TimeUnit.HOURS);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
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
        });

        // Register payload types
        PayloadTypeRegistry.playC2S().register(HandShaker.ModsListPayload.TYPE, HandShaker.ModsListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandShaker.IntegrityPayload.TYPE, HandShaker.IntegrityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VeltonPayload.TYPE, VeltonPayload.CODEC);

        // Register payload handlers
        ServerPlayNetworking.registerGlobalReceiver(HandShaker.ModsListPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String playerName = player.getName().getString();
            try {
                ValidationResult result = payloadValidator.validateModList(
                    player.getUUID(), playerName, payload.mods(), payload.modListHash(), payload.nonce());
                
                if (!result.success) {
                    player.connection.disconnect(Component.literal(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process mod list from {}. Terminating connection.", playerName, e);
                player.connection.disconnect(Component.literal(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HandShaker.IntegrityPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String playerName = player.getName().getString();
            try {
                ValidationResult result = payloadValidator.validateIntegrity(
                    player.getUUID(), playerName, payload.signature(), payload.jarHash(), payload.nonce());
                
                if (!result.success) {
                    player.connection.disconnect(Component.literal(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process integrity payload from {}. Terminating connection.", playerName, e);
                player.connection.disconnect(Component.literal(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(VeltonPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String playerName = player.getName().getString();
            try {
                ValidationResult result = payloadValidator.validateVelton(
                    player.getUUID(), playerName, payload.jarHash(), payload.nonce());
                
                if (!result.success) {
                    player.connection.disconnect(Component.literal(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process Velton payload from {}. Terminating connection.", playerName, e);
                player.connection.disconnect(Component.literal(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });

        // Register player lifecycle events 
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            scheduler.schedule(() -> {
                server.execute(() -> {
                    if (handler.player.connection == null) return; // Player disconnected
                    ClientInfo info = clients.computeIfAbsent(handler.player.getUUID(), uuid -> new ClientInfo(Collections.emptySet(), false, false, null, null, null));

                    configManager.checkPlayer(handler.player, info, false, true); // Timeout check: enforce integrity requirements
                });
            }, configManager.getHandshakeTimeoutSeconds(), TimeUnit.SECONDS);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            payloadValidator.clearNonceHistory(handler.player.getUUID());
            clients.remove(handler.player.getUUID());
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

    public java.util.concurrent.ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    public void checkAllPlayers() {
        if (server == null) return;
        LOGGER.info("Re-checking all online players...");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            configManager.checkPlayer(player, clients.getOrDefault(player.getUUID(), new ClientInfo(Collections.emptySet(), false, false, null, null, null)), false, true);
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

    private void loadPublicCertificate() {
        ServerSecurityWebhookSupport.SecurityMaterial security = ServerSecurityWebhookSupport.loadSecurityMaterial(
            HandShakerServer.class.getClassLoader(),
            "public.cer",
            DEBUG_MODE,
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
        if (configManager == null || !configManager.isRestApiEnabled()) {
            return;
        }

        ApiServerConfig apiConfig = new ApiServerConfig(true, configManager.getRestApiPort(), "");
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
        webhookDispatcher = ServerSecurityWebhookSupport.createWebhookDispatcher(configManager, LOGGER);
    }

    public void publishWebhookKick(String playerName, String reason, String mod) {
        ServerSecurityWebhookSupport.publishKick(webhookDispatcher, playerName, reason, mod);
    }

    public void publishWebhookBan(String playerName, String reason, String mod) {
        ServerSecurityWebhookSupport.publishBan(webhookDispatcher, playerName, reason, mod);
    }


    public record VeltonPayload(byte[] signature, String jarHash, String nonce) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<VeltonPayload> TYPE = PayloadTypeCompat.payloadType("velton", "signature");
        public static final StreamCodec<ByteBuf, VeltonPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE_ARRAY, VeltonPayload::signature,
                ByteBufCodecs.STRING_UTF8, VeltonPayload::jarHash,
                ByteBufCodecs.STRING_UTF8, VeltonPayload::nonce,
                VeltonPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

}
