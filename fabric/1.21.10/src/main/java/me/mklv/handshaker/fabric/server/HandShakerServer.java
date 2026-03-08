package me.mklv.handshaker.fabric.server;

import me.mklv.handshaker.fabric.HandShaker;
import me.mklv.handshaker.fabric.server.configs.ConfigManager;
import me.mklv.handshaker.common.configs.ConfigMigration.ConfigMigrator;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;
import me.mklv.handshaker.common.database.H2PlayerHistoryDatabase;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.protocols.CertLoader;
import me.mklv.handshaker.common.protocols.LegacyVersion;
import me.mklv.handshaker.common.protocols.PayloadValidation;
import me.mklv.handshaker.common.protocols.PayloadValidation.PayloadValidationCallbacks;
import me.mklv.handshaker.common.protocols.PayloadValidation.ValidationResult;
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
    public static final Identifier VELTON_CHANNEL = Identifier.of("velton", "signature");
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + "-server");
    private static HandShakerServer instance;
    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private ConfigManager configManager;
    private PlayerHistoryDatabase playerHistoryDb;
    private PayloadValidation payloadValidator;
    private MinecraftServer server;
    private PublicKey publicKey;
    private SignatureVerifier signatureVerifier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static HandShakerServer getInstance() {
        return instance;
    }

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
            playerHistoryDb = new H2PlayerHistoryDatabase(configDir.toFile(), LoggerAdapter.fromLoaderDatabaseLogger(LOGGER));
        }

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
                        playerHistoryDb.syncPlayerMods(playerId, playerName, mods);
                    }
                }

                @Override
                public void checkPlayer(UUID playerId, String playerName, ClientInfo info) {
                    if (server == null) return;
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                    if (player != null) {
                        configManager.checkPlayer(player, info, false);
                    }
                }
            },
            signatureVerifier,
            clients
        );
        payloadValidator.setDebugMode(DEBUG_MODE);

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
                ValidationResult result = payloadValidator.validateModList(
                    player.getUuid(), playerName, payload.mods(), payload.modListHash(), payload.nonce());
                
                if (!result.success) {
                    player.networkHandler.disconnect(Text.of(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process mod list from {}. Terminating connection.", playerName, e);
                player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HandShaker.IntegrityPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            try {
                ValidationResult result = payloadValidator.validateIntegrity(
                    player.getUuid(), playerName, payload.signature(), payload.jarHash(), payload.nonce());
                
                if (!result.success) {
                    player.networkHandler.disconnect(Text.of(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process integrity payload from {}. Terminating connection.", playerName, e);
                player.networkHandler.disconnect(Text.of(configManager.getMessageOrDefault(
                    StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED)));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(VeltonPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            try {
                ValidationResult result = payloadValidator.validateVelton(
                    player.getUuid(), playerName, payload.jarHash(), payload.nonce());
                
                if (!result.success) {
                    player.networkHandler.disconnect(Text.of(result.errorMessage));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process Velton payload from {}. Terminating connection.", playerName, e);
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

                    configManager.checkPlayer(handler.player, info, false, true); // Timeout check: enforce integrity requirements
                });
            }, configManager.getHandshakeTimeoutSeconds(), TimeUnit.SECONDS);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            payloadValidator.clearNonceHistory(handler.player.getUuid());
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
            configManager.checkPlayer(player, clients.getOrDefault(player.getUuid(), new ClientInfo(Collections.emptySet(), false, false, null, null, null)), false, true);
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
                if (DEBUG_MODE) {
                    LOGGER.info(message);
                }
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
                    if (DEBUG_MODE) {
                        LOGGER.info(message);
                    }
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