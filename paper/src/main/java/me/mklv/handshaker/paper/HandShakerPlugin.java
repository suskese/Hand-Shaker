package me.mklv.handshaker.paper;

import me.mklv.handshaker.common.configs.ConfigMigration.ConfigMigrator;
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
        playerHistoryDb = new SQLitePlayerHistoryDatabase(getDataFolder(), LoggerAdapter.fromLoaderDatabaseLogger(getLogger()), configManager.isPlayerdbEnabled());
    }

    @Override
    public void onDisable() {
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
}
