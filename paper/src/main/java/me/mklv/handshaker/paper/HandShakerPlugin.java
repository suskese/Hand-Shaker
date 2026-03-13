package me.mklv.handshaker.paper;

import me.mklv.handshaker.common.configs.ConfigMigration.ConfigMigrator;
import me.mklv.handshaker.common.api.local.ApiDataProvider;
import me.mklv.handshaker.common.api.local.ApiModels;
import me.mklv.handshaker.common.api.local.ApiServerConfig;
import me.mklv.handshaker.common.api.local.LocalRestApiServer;
import me.mklv.handshaker.common.api.discord.WebhookConfig;
import me.mklv.handshaker.common.api.discord.WebhookDispatcher;
import me.mklv.handshaker.common.api.discord.WebhookEventType;
import me.mklv.handshaker.paper.utils.HandShakerListener;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.database.SQLitePlayerHistoryDatabase;
import me.mklv.handshaker.paper.utils.PluginProtocolHandler;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.protocols.LegacyVersion;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HandShakerPlugin extends JavaPlugin {
    public static boolean DEBUG = false;
    
    // Plugin channels for communication
    public static final String MODS_CHANNEL = "hand-shaker:mods";
    public static final String INTEGRITY_CHANNEL = "hand-shaker:integrity";
    public static final String VELTON_CHANNEL = "velton:signature";

    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();
    private ConfigManager configManager;
    private PlayerHistoryDatabase playerHistoryDb;
    private PluginProtocolHandler protocolHandler;
    private LocalRestApiServer localRestApiServer;
    private WebhookDispatcher webhookDispatcher;

    @Override
    public void onEnable()
 {        loadConfiguration();
        LegacyVersion.initializeTrustedHybridHashes(getClass(), new LegacyVersion.LogSink() {
            @Override
            public void info(String message) {
                getLogger().info(message);
            }

            @Override
            public void warn(String message) {
                getLogger().warning(message);
            }
        }, DEBUG);

        loadDatabase();
        startLocalRestApiIfEnabled();
        startWebhookIfEnabled();

        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (protocolHandler != null) {
                protocolHandler.getPayloadValidator().cleanupExpiredNoncesNow();
            }
        }, 5, 5, java.util.concurrent.TimeUnit.MINUTES);

        // Schedule periodic history cleanup (runs every hour if delete-history-days > 0)
        int historyDays = configManager.getDeleteHistoryDays();
        if (historyDays > 0) {
            getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
                int days = configManager.getDeleteHistoryDays();
                if (days > 0 && playerHistoryDb != null) {
                    playerHistoryDb.deleteOldHistory(days);
                }
            }, 1, 1, java.util.concurrent.TimeUnit.HOURS);
        }

        // Initialize protocol handler (handles plugin channels and certificate loading)
        protocolHandler = new PluginProtocolHandler(this, clients);
        protocolHandler.registerPluginChannels();
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new HandShakerListener(this, clients), this);
        
        getLogger().info("HandShaker plugin enabled (Paper/Folia compatible)");
        
        if (HandShakerPlugin.DEBUG){
            getLogger().warning("YOU ARE RUNNING A DEVELOPMENT BUILD OF HANDSHAKER - EXPECT BUGS AND ISSUES");
        }
    }

    @Override
    public void onLoad() {
        this.getLifecycleManager().registerEventHandler(
            LifecycleEvents.COMMANDS,
            event -> HandShakerCommand.register(this, event.registrar())
        );
    }

    private void loadConfiguration() {
        // Migrate v3 config to v4 if needed
        ConfigMigrator.migrateIfNeeded(
            getDataFolder().toPath(),
            new ConfigMigrator.Logger() {
                @Override
                public void info(String message) {
                    getLogger().info(message);
                }

                @Override
                public void warn(String message) {
                    getLogger().warning(message);
                }

                @Override
                public void error(String message, Throwable error) {
                    getLogger().severe(message + " (" + error.getClass().getSimpleName() + ": " + error.getMessage() + ")");
                }
            }
        );

        configManager = new ConfigManager(this);
        configManager.load();

        // Sync global debug flag from config
        DEBUG = configManager.isDebug();
    }

    private void loadDatabase() {
        PlayerHistoryDatabase.DatabaseOptions options = PlayerHistoryDatabase.DatabaseOptions.of(
            configManager.getDatabasePoolSize(),
            configManager.getDatabaseIdleTimeoutMs(),
            configManager.getDatabaseMaxLifetimeMs()
        );
        playerHistoryDb = new SQLitePlayerHistoryDatabase(
            getDataFolder(),
            LoggerAdapter.fromLoaderDatabaseLogger(getLogger()),
            configManager.isPlayerdbEnabled(),
            options
        );
    }

    @Override
    public void onDisable() {
        if (webhookDispatcher != null) {
            webhookDispatcher.shutdown();
            webhookDispatcher = null;
        }
        if (localRestApiServer != null) {
            localRestApiServer.stop();
            localRestApiServer = null;
        }
        if (protocolHandler != null) {
            protocolHandler.unregisterPluginChannels();
        }
        if (playerHistoryDb != null) {
            playerHistoryDb.close();
        }
        clients.clear();
        getLogger().info("HandShaker plugin disabled");
    }

    public void checkPlayer(Player player) {
        checkPlayer(player, false);
    }

    public void checkPlayer(Player player, boolean isTimeoutCheck) {
        if (protocolHandler != null) {
            protocolHandler.checkPlayer(player, clients, isTimeoutCheck);
        }
    }

    public void clearNonceHistory(UUID playerId) {
        if (protocolHandler != null) {
            protocolHandler.clearNonceHistory(playerId);
        }
    }

    public void schedulePlayerCheck(Player player, long delayTicks) {
        this.getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
            if (player.isOnline()) {
                checkPlayer(player, true); // true = isTimeoutCheck, enforce integrity verification
            }
        }, delayTicks);
    }

    public void checkAllPlayers() {
        getLogger().info("Re-checking all online players...");
        // Reset checked flags for all players
        for (UUID uuid : new java.util.HashSet<>(clients.keySet())) {
            ClientInfo info = clients.get(uuid);
            if (info != null) {
                clients.put(uuid, info.withChecked(false));
            }
        }
        // Re-check all online players with timeout enforcement
        for (Player player : getServer().getOnlinePlayers()) {
            checkPlayer(player, true);
        }
    }

    // Getters
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerHistoryDatabase getPlayerHistoryDb() {
        return playerHistoryDb;
    }

    public Map<UUID, ClientInfo> getClients() {
        return clients;
    }
    
    public void recordPlayerJoin(UUID uuid) {
        if (DEBUG) {
            joinTimestamps.put(uuid, System.currentTimeMillis());
        }
    }

    public Long getJoinTimestamp(UUID uuid) {
        return joinTimestamps.get(uuid);
    }

    public Long removeJoinTimestamp(UUID uuid) {
        return joinTimestamps.remove(uuid);
    }

    public void publishWebhookKick(String playerName, String reason, String mod) {
        if (webhookDispatcher != null) {
            webhookDispatcher.publish(WebhookEventType.PLAYER_KICKED, playerName, mod, reason);
        }
    }

    public void publishWebhookBan(String playerName, String reason, String mod) {
        if (webhookDispatcher != null) {
            webhookDispatcher.publish(WebhookEventType.PLAYER_BANNED, playerName, mod, reason);
        }
    }

    private void startLocalRestApiIfEnabled() {
        if (configManager == null || !configManager.isRestApiEnabled()) {
            return;
        }

        ApiServerConfig apiConfig = new ApiServerConfig(true, configManager.getRestApiPort(), "");
        localRestApiServer = new LocalRestApiServer(apiConfig, createApiProvider(), LoggerFactory.getLogger("hand-shaker-paper-api"));
        try {
            localRestApiServer.start();
        } catch (IOException e) {
            getLogger().warning("Failed to start local REST API: " + e.getMessage());
        }
    }

    private ApiDataProvider createApiProvider() {
        return new ApiDataProvider() {
            @Override
            public List<ApiModels.PlayerSummary> getActivePlayers() {
                Map<UUID, ApiModels.PlayerSummary> merged = new LinkedHashMap<>();
                if (playerHistoryDb != null && playerHistoryDb.isEnabled()) {
                    for (PlayerHistoryDatabase.PlayerSummaryInfo entry : playerHistoryDb.getPlayersWithActiveMods()) {
                        merged.put(entry.uuid(), new ApiModels.PlayerSummary(
                            entry.uuid().toString(),
                            entry.currentName(),
                            entry.modCount()
                        ));
                    }
                }

                for (Player online : getServer().getOnlinePlayers()) {
                    ClientInfo info = clients.get(online.getUniqueId());
                    int modCount = info != null && info.mods() != null ? info.mods().size() : 0;
                    merged.put(online.getUniqueId(), new ApiModels.PlayerSummary(
                        online.getUniqueId().toString(),
                        online.getName(),
                        modCount
                    ));
                }

                return new ArrayList<>(merged.values());
            }

            @Override
            public List<ApiModels.PlayerMod> getPlayerMods(String playerUuid) {
                List<ApiModels.PlayerMod> mods = new ArrayList<>();
                UUID uuid;
                try {
                    uuid = UUID.fromString(playerUuid);
                } catch (Exception ignored) {
                    return mods;
                }

                Map<String, ApiModels.PlayerMod> merged = new LinkedHashMap<>();
                if (playerHistoryDb != null && playerHistoryDb.isEnabled()) {
                    for (PlayerHistoryDatabase.ModHistoryEntry entry : playerHistoryDb.getPlayerHistory(uuid)) {
                        merged.put(entry.modName(), new ApiModels.PlayerMod(
                            entry.modName(),
                            entry.isActive(),
                            entry.getAddedDateFormatted(),
                            entry.getRemovedDateFormatted()
                        ));
                    }
                }

                ClientInfo live = clients.get(uuid);
                if (live != null && live.mods() != null) {
                    for (String mod : live.mods()) {
                        merged.put(mod, new ApiModels.PlayerMod(mod, true, null, null));
                    }
                }

                mods.addAll(merged.values());
                return mods;
            }

            @Override
            public List<ApiModels.ModSummary> getAllMods() {
                List<ApiModels.ModSummary> mods = new ArrayList<>();
                if (playerHistoryDb != null && playerHistoryDb.isEnabled()) {
                    for (Map.Entry<String, Integer> entry : playerHistoryDb.getModPopularity().entrySet()) {
                        mods.add(new ApiModels.ModSummary(entry.getKey(), entry.getValue()));
                    }
                    return mods;
                }

                Map<String, Integer> counts = new LinkedHashMap<>();
                for (ClientInfo info : clients.values()) {
                    if (info == null || info.mods() == null) {
                        continue;
                    }
                    for (String mod : info.mods()) {
                        counts.put(mod, counts.getOrDefault(mod, 0) + 1);
                    }
                }
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    mods.add(new ApiModels.ModSummary(entry.getKey(), entry.getValue()));
                }
                return mods;
            }

            @Override
            public List<ApiModels.ModHistoryPlayer> getModHistoryPlayers(String modToken) {
                if (playerHistoryDb == null || !playerHistoryDb.isEnabled()) {
                    return Collections.emptyList();
                }
                return playerHistoryDb.getPlayersWithMod(modToken).stream()
                    .map(p -> new ApiModels.ModHistoryPlayer(
                        p.uuid().toString(),
                        p.currentName(),
                        p.firstSeen().toString(),
                        p.isActive()
                    ))
                    .collect(java.util.stream.Collectors.toList());
            }

            @Override
            public PlayerHistoryDatabase.PoolStats getPoolStats() {
                if (playerHistoryDb == null || !playerHistoryDb.isEnabled()) {
                    return new PlayerHistoryDatabase.PoolStats(0, 0, 0, -1);
                }
                return playerHistoryDb.getPoolStats();
            }

            @Override
            public boolean isDatabaseEnabled() {
                return playerHistoryDb != null && playerHistoryDb.isEnabled();
            }

            @Override
            public String getDatabaseType() {
                return playerHistoryDb != null ? playerHistoryDb.getClass().getSimpleName() : "none";
            }
        };
    }

    private void startWebhookIfEnabled() {
        if (configManager == null || !configManager.isWebhookEnabled()) {
            return;
        }

        EnumSet<WebhookEventType> events = EnumSet.noneOf(WebhookEventType.class);
        if (configManager.isWebhookNotifyOnKick()) {
            events.add(WebhookEventType.PLAYER_KICKED);
        }
        if (configManager.isWebhookNotifyOnBan()) {
            events.add(WebhookEventType.PLAYER_BANNED);
        }

        WebhookConfig webhookConfig = new WebhookConfig(
            true,
            configManager.getWebhookUrl(),
            "",
            events
        );
        webhookDispatcher = new WebhookDispatcher(webhookConfig, LoggerFactory.getLogger("hand-shaker-paper-webhook"));
    }
}
