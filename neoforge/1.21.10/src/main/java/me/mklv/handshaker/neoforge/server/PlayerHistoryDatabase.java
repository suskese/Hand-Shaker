package me.mklv.handshaker.neoforge.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.neoforged.fml.loading.FMLPaths;

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

    public PlayerHistoryDatabase() {
        this.dbFile = new File(FMLPaths.CONFIGDIR.get().toFile(), "hand-shaker-history");
        initialize();
    }

    private void initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL;AUTO_SERVER=TRUE");
            config.setDriverClassName("org.h2.Driver");
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);

            createTables();
            migrateSchemaIfNeeded();
            HandShakerServerMod.LOGGER.info("Player history database initialized at: {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            HandShakerServerMod.LOGGER.error("Failed to initialize player history database", e);
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
            HandShakerServerMod.LOGGER.error("Failed to create database tables", e);
        }
    }

    private void migrateSchemaIfNeeded() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='PLAYER_NAMES'")) {
            
            boolean hasOldNameColumn = false;
            boolean hasNewCurrentNameColumn = false;
            
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                if ("NAME".equals(columnName)) {
                    hasOldNameColumn = true;
                }
                if ("CURRENT_NAME".equals(columnName)) {
                    hasNewCurrentNameColumn = true;
                }
            }
            
            // If table is fresh (neither column exists), createTables() already created the correct schema
            if (!hasOldNameColumn && !hasNewCurrentNameColumn) {
                HandShakerServerMod.LOGGER.debug("Fresh database detected - schema is correct");
                return;
            }
            
            // If new schema already exists, no migration needed
            if (hasNewCurrentNameColumn && !hasOldNameColumn) {
                HandShakerServerMod.LOGGER.debug("Database already has correct schema");
                return;
            }
            
            // Migrate from old schema to new schema
            if (hasOldNameColumn && !hasNewCurrentNameColumn) {
                HandShakerServerMod.LOGGER.info("Migrating database schema from old to new format");
                
                try (Connection migConn = dataSource.getConnection()) {
                    migConn.setAutoCommit(false);
                    try (Statement migStmt = migConn.createStatement()) {
                        // Rename old table
                        migStmt.execute("ALTER TABLE player_names RENAME TO player_names_old");
                        
                        // Create new table with correct schema
                        migStmt.execute("""
                                CREATE TABLE player_names (
                                    uuid TEXT PRIMARY KEY,
                                    current_name TEXT NOT NULL,
                                    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                )""");
                        
                        // Migrate data: for each uuid, take the most recent name
                        migStmt.execute("""
                                INSERT INTO player_names (uuid, current_name, first_seen, last_seen)
                                SELECT uuid, name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM player_names_old""");
                        
                        // Drop old table
                        migStmt.execute("DROP TABLE player_names_old");
                        
                        migConn.commit();
                        HandShakerServerMod.LOGGER.info("Database schema migration completed successfully");
                    } catch (SQLException e) {
                        migConn.rollback();
                        HandShakerServerMod.LOGGER.error("Failed to migrate database schema", e);
                        throw e;
                    }
                }
            }
        } catch (SQLException e) {
            HandShakerServerMod.LOGGER.warn("Schema migration check failed, database may need manual intervention", e);
        }
    }

    /**
     * Sync player's current mod list with database
     */
    public void syncPlayerMods(UUID uuid, String playerName, Set<String> currentMods) {
        if (dataSource == null) return;
        
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Upsert player name using MERGE (H2 compatible)
                String upsertPlayer = """
                    MERGE INTO player_names (uuid, current_name, first_seen, last_seen) KEY(uuid)
                    VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """;
                try (PreparedStatement ps = conn.prepareStatement(upsertPlayer)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, playerName);
                    ps.executeUpdate();
                }
                
                // Get active mods from DB
                Set<String> dbActiveMods = getActiveModsForSync(conn, uuid);
                
                // Calculate diffs
                Set<String> newMods = new HashSet<>(currentMods);
                newMods.removeAll(dbActiveMods);
                Set<String> removedMods = new HashSet<>(dbActiveMods);
                removedMods.removeAll(currentMods);
                
                // Batch insert new mods
                if (!newMods.isEmpty()) {
                    String insertMod = """
                        INSERT INTO mod_history (player_uuid, mod_name, added_date, removed_date)
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
                modPopularityCache.invalidate();
            } catch (SQLException e) {
                conn.rollback();
                HandShakerServerMod.LOGGER.warn("Failed to sync player mods: {}", e.getMessage());
            }
        } catch (SQLException e) {
            HandShakerServerMod.LOGGER.warn("Database connection failed: {}", e.getMessage());
        }
    }

    /**
     * Get currently active mods for a player (for internal sync operations)
     */
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

    /**
     * Get full history for a player including add/remove dates
     */
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
            HandShakerServerMod.LOGGER.warn("Failed to get player history: {}", e.getMessage());
        }

        return history;
    }

    /**
     * Get mod popularity (how many unique players have used each mod)
     */
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
            HandShakerServerMod.LOGGER.warn("Failed to get mod popularity: {}", e.getMessage());
        }

        return popularity;
    }

    /**
     * Get list of players who have used a specific mod
     */
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
            HandShakerServerMod.LOGGER.warn("Failed to get players with mod: {}", e.getMessage());
        }

        return players;
    }

    /**
     * Get all known names for a player UUID
     */
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
            HandShakerServerMod.LOGGER.warn("Failed to get player names: {}", e.getMessage());
        }

        return names;
    }

    /**
     * Get count of unique players with active mods
     */
    public int getUniqueActivePlayers() {
        if (dataSource == null) return 0;
        
        String sql = "SELECT COUNT(DISTINCT player_uuid) as player_count FROM mod_history WHERE removed_date IS NULL";
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("player_count");
            }
        } catch (SQLException e) {
            HandShakerServerMod.LOGGER.warn("Failed to get unique active players: {}", e.getMessage());
        }
        
        return 0;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            HandShakerServerMod.LOGGER.info("Player history database closed");
        }
    }

    /**
     * Internal cache for time-sensitive data
     */
    private static class CachedValue<T> {
        private T value;
        private long expiresAt;
        private final long ttlMs;

        CachedValue(long ttlMs) {
            this.ttlMs = ttlMs;
        }

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

        synchronized void invalidate() {
            this.value = null;
            this.expiresAt = 0;
        }
    }

    // Data classes
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
