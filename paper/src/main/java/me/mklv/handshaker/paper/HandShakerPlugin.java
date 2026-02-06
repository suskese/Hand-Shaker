package me.mklv.handshaker.paper;

import me.mklv.handshaker.paper.configs.ConfigManager;
import me.mklv.handshaker.paper.configs.ConfigMigrator;
import me.mklv.handshaker.paper.listener.HandShakerListener;
import me.mklv.handshaker.paper.protocol.PluginProtocolHandler;
import me.mklv.handshaker.paper.utils.PlayerHistoryDatabase;
import me.mklv.handshaker.paper.utils.ClientInfo;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HandShakerPlugin extends JavaPlugin {
    public static boolean DEBUG = true;
    
    // Plugin channels for communication
    public static final String MODS_CHANNEL = "hand-shaker:mods";
    public static final String INTEGRITY_CHANNEL = "hand-shaker:integrity";
    public static final String VELTON_CHANNEL = "velton:signature";

    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private ConfigManager configManager;
    private PlayerHistoryDatabase playerHistoryDb;
    private PluginProtocolHandler protocolHandler;

    @Override
    public void onEnable() {
        loadConfiguration();
        loadDatabase();
        
        // Initialize protocol handler (handles plugin channels and certificate loading)
        protocolHandler = new PluginProtocolHandler(this, clients);
        protocolHandler.registerPluginChannels();
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new HandShakerListener(this, clients), this);
        
        // Register commands (Paper doesn't use YAML command declarations)
        HandShakerCommand.register(this);
        
        getLogger().info("HandShaker plugin enabled (Paper/Folia compatible)");
        
        if (HandShakerPlugin.DEBUG){
            getLogger().warning("YOU ARE RUNNING A DEVELOPMENT BUILD OF HANDSHAKER - EXPECT BUGS AND ISSUES");
        }
    }

    private void loadConfiguration() {
        // Migrate v3 config to v4 if needed
        ConfigMigrator migrator = new ConfigMigrator(this);
        migrator.migrateIfNeeded();

        configManager = new ConfigManager(this);
        configManager.load();
    }

    private void loadDatabase() {
        playerHistoryDb = new PlayerHistoryDatabase(getDataFolder(), getLogger(), configManager.isPlayerdbEnabled());
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

    public boolean validateAndSyncModList(Player player, String payload, String modListHash, String nonce) {
        if (protocolHandler != null) {
            return protocolHandler.validateAndSyncModList(player, payload, modListHash, nonce);
        }
        return false;
    }

    public void handleIntegrityCheck(Player player, byte[] clientSignature, String jarHash, String nonce) {
        if (protocolHandler != null) {
            protocolHandler.handleIntegrityCheck(player, clientSignature, jarHash, nonce);
        }
    }

    public void handleVeltonPayload(Player player, byte[] clientSignature, String jarHash, String nonce) {
        if (protocolHandler != null) {
            protocolHandler.handleVeltonPayload(player, clientSignature, jarHash, nonce);
        }
    }

    public void checkPlayer(Player player) {
        if (protocolHandler != null) {
            protocolHandler.checkPlayer(player, clients);
        }
    }

    public void schedulePlayerCheck(Player player, long delayTicks) {
        this.getServer().getGlobalRegionScheduler().runDelayed(this, task -> {
            if (player.isOnline()) {
                checkPlayer(player);
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
        // Re-check all online players
        for (Player player : getServer().getOnlinePlayers()) {
            checkPlayer(player);
        }
    }

    // Getters
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerHistoryDatabase getPlayerHistoryDb() {
        return playerHistoryDb;
    }

    public PluginProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public Set<String> getClientMods(UUID uuid) {
        ClientInfo info = clients.get(uuid);
        return info != null ? info.mods() : null;
    }

    public Map<UUID, ClientInfo> getClients() {
        return clients;
    }
}
