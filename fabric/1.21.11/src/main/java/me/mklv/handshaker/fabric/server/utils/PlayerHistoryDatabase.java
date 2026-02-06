package me.mklv.handshaker.fabric.server.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import me.mklv.handshaker.fabric.server.HandShakerServer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PlayerHistoryDatabase {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final long CACHE_TTL_MS = 30_000; // 30-second cache for mod popularity
    
    private HikariDataSource dataSource;
    private final File dbFile;
    
    // Cache for frequently accessed data
    private final CachedValue<Map<String, Integer>> modPopularityCache = new CachedValue<>(CACHE_TTL_MS);
    private boolean enabled = false;

    public PlayerHistoryDatabase(boolean enabled) {
        File dataFolder = new File(FabricLoader.getInstance().getConfigDir().toFile(), "HandShaker/data");
        dataFolder.mkdirs();
        this.dbFile = new File(dataFolder, "hand-shaker-history.db");
        this.enabled = enabled;
        if (enabled) {
            initialize();
        }
    }

    private void initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:" + dbFile.getAbsolutePath().replace(".db", ""));
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            createTables();
            HandShakerServer.LOGGER.info("Player history database initialized at: {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            HandShakerServer.LOGGER.error("Failed to initialize player history database", e);
            enabled = false;
        }
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_names (
                    uuid TEXT PRIMARY KEY,
                    current_name TEXT NOT NULL,
                    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mod_history (
                    id INTEGER PRIMARY KEY AUTO_INCREMENT,
                    player_uuid TEXT NOT NULL,
                    mod_name TEXT NOT NULL,
                    added_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    removed_date TIMESTAMP,
                    FOREIGN KEY (player_uuid) REFERENCES player_names(uuid),
                    UNIQUE(player_uuid, mod_name, added_date)
                )
                """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_history_uuid ON mod_history(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_history_mod ON mod_history(mod_name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_uuid ON player_names(uuid)");
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to create database tables", e);
        }
    }


    public void syncPlayerMods(UUID uuid, String playerName, Set<String> currentMods) {
        if (dataSource == null || !enabled) return;
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Upsert player name
                String upsertPlayer = """
                    MERGE INTO player_names (uuid, current_name, first_seen, last_seen) KEY(uuid)
                    VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """;
                try (PreparedStatement ps = conn.prepareStatement(upsertPlayer)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, playerName);
                    ps.executeUpdate();
                }
                
                // Get active mods from DB in single query
                Set<String> dbActiveMods = getActiveModsForSync(conn, uuid);
                
                // Calculate diffs
                Set<String> newMods = new HashSet<>(currentMods);
                newMods.removeAll(dbActiveMods);
                Set<String> removedMods = new HashSet<>(dbActiveMods);
                removedMods.removeAll(currentMods);
                
                // Batch insert new mods using UPSERT
                if (!newMods.isEmpty()) {
                    String insertMod = """
                        MERGE INTO mod_history (player_uuid, mod_name, added_date, removed_date) KEY(player_uuid, mod_name, added_date)
                        VALUES (?, ?, CURRENT_TIMESTAMP, NULL)
                        """;
                    try (PreparedStatement ps = conn.prepareStatement(insertMod)) {
                        for (String mod : newMods) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, mod);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                
                // Batch mark mods as removed
                if (!removedMods.isEmpty()) {
                    String removeMod = "UPDATE mod_history SET removed_date = CURRENT_TIMESTAMP WHERE player_uuid = ? AND mod_name = ? AND removed_date IS NULL";
                    try (PreparedStatement ps = conn.prepareStatement(removeMod)) {
                        for (String mod : removedMods) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, mod);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }
                
                conn.commit();
                modPopularityCache.invalidate(); // Invalidate cache after changes
            } catch (SQLException e) {
                conn.rollback();
                HandShakerServer.LOGGER.warn("Failed to sync player mods: {}", e.getMessage());
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.warn("Database connection failed: {}", e.getMessage());
        }
    }

    private Set<String> getActiveModsForSync(Connection conn, UUID uuid) throws SQLException {
        Set<String> mods = new HashSet<>();
        String sql = "SELECT mod_name FROM mod_history WHERE player_uuid = ? AND removed_date IS NULL";
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                mods.add(rs.getString("mod_name"));
            }
        }
        return mods;
    }

    public List<ModHistoryEntry> getPlayerHistory(UUID uuid) {
        List<ModHistoryEntry> history = new ArrayList<>();
        String sql = """
            SELECT mod_name, added_date, removed_date 
            FROM mod_history 
            WHERE player_uuid = ? 
            ORDER BY added_date DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                history.add(new ModHistoryEntry(
                    rs.getString("mod_name"),
                    rs.getTimestamp("added_date").toLocalDateTime(),
                    rs.getTimestamp("removed_date") != null ? rs.getTimestamp("removed_date").toLocalDateTime() : null
                ));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.warn("Failed to get player history: {}", e.getMessage());
        }

        return history;
    }

    public Map<String, Integer> getModPopularity() {
        Map<String, Integer> cached = modPopularityCache.get();
        if (cached != null) {
            return cached;
        }

        Map<String, Integer> popularity = new LinkedHashMap<>();
        String sql = """
            SELECT mod_name, COUNT(DISTINCT player_uuid) as player_count 
            FROM mod_history 
            WHERE removed_date IS NULL
            GROUP BY mod_name 
            ORDER BY player_count DESC, mod_name ASC
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                popularity.put(rs.getString("mod_name"), rs.getInt("player_count"));
            }
            modPopularityCache.set(popularity);
        } catch (SQLException e) {
            HandShakerServer.LOGGER.warn("Failed to get mod popularity: {}", e.getMessage());
        }

        return popularity;
    }

    public List<PlayerModInfo> getPlayersWithMod(String modName) {
        List<PlayerModInfo> players = new ArrayList<>();
        String sql = """
            SELECT 
                mh.player_uuid,
                pn.current_name,
                MIN(mh.added_date) as first_seen,
                MAX(CASE WHEN mh.removed_date IS NULL THEN 1 ELSE 0 END) as is_active
            FROM mod_history mh
            JOIN player_names pn ON mh.player_uuid = pn.uuid
            WHERE mh.mod_name = ?
            GROUP BY mh.player_uuid, pn.current_name
            ORDER BY is_active DESC, first_seen DESC
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                players.add(new PlayerModInfo(
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("current_name"),
                    rs.getTimestamp("first_seen").toLocalDateTime(),
                    rs.getInt("is_active") == 1
                ));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.warn("Failed to get players with mod: {}", e.getMessage());
        }

        return players;
    }

    public List<String> getPlayerNames(UUID uuid) {
        List<String> names = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT current_name FROM player_names WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                names.add(rs.getString("current_name"));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.warn("Failed to get player names: {}", e.getMessage());
        }

        return names;
    }

    public int getUniqueActivePlayers() {
        if (dataSource == null || !enabled) return 0;
        
        String sql = "SELECT COUNT(DISTINCT player_uuid) as player_count FROM mod_history WHERE removed_date IS NULL";
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("player_count");
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.warn("Failed to get unique active players: {}", e.getMessage());
        }
        
        return 0;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            HandShakerServer.LOGGER.info("Player history database closed");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static class CachedValue<T> {
        private T value;
        private long expiresAt;
        private final long ttlMs;

        @SuppressWarnings("null")
        CachedValue(long ttlMs) {
            this.ttlMs = ttlMs;
        }

        @SuppressWarnings("null")
        synchronized T get() {
            if (value != null && System.currentTimeMillis() < expiresAt) {
                return value;
            }
            return null;
        }

        synchronized void set(T newValue) {
            this.value = newValue;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        @SuppressWarnings("null")
        synchronized void invalidate() {
            this.value = null;
            this.expiresAt = 0;
        }
    }

    // Records for data transfer
    public record ModHistoryEntry(String modName, LocalDateTime addedDate, LocalDateTime removedDate) {
        public String getAddedDateFormatted() {
            return addedDate != null ? addedDate.format(DATE_FORMAT) : "Unknown";
        }

        public String getRemovedDateFormatted() {
            return removedDate != null ? removedDate.format(DATE_FORMAT) : null;
        }

        public boolean isActive() {
            return removedDate == null;
        }
    }

    public record PlayerModInfo(UUID uuid, String currentName, LocalDateTime firstSeen, boolean isActive) {
        public String getFirstSeenFormatted() {
            return firstSeen != null ? firstSeen.format(DATE_FORMAT) : "Unknown";
        }
    }
}
