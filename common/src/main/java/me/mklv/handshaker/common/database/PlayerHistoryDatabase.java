package me.mklv.handshaker.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import me.mklv.handshaker.common.utils.CachedValue;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;

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
    private final Logger logger;
    
    // Cache for frequently accessed data
    private final CachedValue<Map<String, Integer>> modPopularityCache = new CachedValue<>(CACHE_TTL_MS);

    public boolean isEnabled() {
        return dataSource != null && !dataSource.isClosed();
    }

    public PlayerHistoryDatabase(File configDir, Logger logger) {
        this.dbFile = new File(configDir, "hand-shaker-history");
        this.logger = logger;
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
            logger.info("Player history database initialized at: {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to initialize player history database", e);
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

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mod_registry (
                    mod_id TEXT NOT NULL,
                    mod_version TEXT NOT NULL,
                    mod_hash TEXT,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (mod_id, mod_version)
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_registry_id ON mod_registry(mod_id)");
        } catch (SQLException e) {
            logger.error("Failed to create database tables", e);
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
                logger.debug("Fresh database detected - schema is correct");
                return;
            }
            
            // If new schema already exists, no migration needed
            if (hasNewCurrentNameColumn && !hasOldNameColumn) {
                logger.debug("Database already has correct schema");
                return;
            }
            
            // Migrate from old schema to new schema
            if (hasOldNameColumn && !hasNewCurrentNameColumn) {
                logger.info("Migrating database schema from old to new format");
                
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
                        logger.info("Database schema migration completed successfully");
                    } catch (SQLException e) {
                        migConn.rollback();
                        logger.error("Failed to migrate database schema", e);
                        throw e;
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Schema migration check failed, database may need manual intervention", e);
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
                
                Set<String> normalizedCurrentMods = new HashSet<>();
                if (currentMods != null) {
                    for (String modToken : currentMods) {
                        String normalized = normalizeHistoryModToken(modToken);
                        if (normalized != null && !normalized.isBlank()) {
                            normalizedCurrentMods.add(normalized);
                        }
                    }
                }

                // Get active mods from DB
                Set<String> dbActiveMods = getActiveModsForSync(conn, uuid);
                
                // Calculate diffs
                Set<String> newMods = new HashSet<>(normalizedCurrentMods);
                newMods.removeAll(dbActiveMods);
                Set<String> removedMods = new HashSet<>(dbActiveMods);
                removedMods.removeAll(normalizedCurrentMods);
                
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
                logger.warn("Failed to sync player mods: {}", e.getMessage());
            }
        } catch (SQLException e) {
            logger.warn("Database connection failed: {}", e.getMessage());
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
            logger.warn("Failed to get player history: {}", e.getMessage());
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
            logger.warn("Failed to get mod popularity: {}", e.getMessage());
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
            logger.warn("Failed to get players with mod: {}", e.getMessage());
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
            logger.warn("Failed to get player names: {}", e.getMessage());
        }

        return names;
    }

    public Optional<UUID> getPlayerUuidByName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return Optional.empty();
        }
        String sql = "SELECT uuid FROM player_names WHERE LOWER(current_name) = LOWER(?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            logger.warn("Failed to get player uuid by name: {}", e.getMessage());
        }
        return Optional.empty();
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
            logger.warn("Failed to get unique active players: {}", e.getMessage());
        }
        
        return 0;
    }

    public void registerModFingerprint(String modToken, boolean versioningEnabled) {
        ModEntry entry = ModEntry.parse(modToken);
        if (entry == null) {
            return;
        }
        registerModFingerprint(entry.modId(), versioningEnabled ? entry.version() : null, entry.hash());
    }

    public void registerModFingerprint(String modId, String modVersion, String modHash) {
        if (dataSource == null || modId == null || modId.isBlank()) {
            return;
        }

        String normalizedId = modId.toLowerCase(Locale.ROOT);
        String normalizedVersion = normalizeVersion(modVersion);
        String normalizedHash = normalizeHash(modHash);
        String sql = """
            MERGE INTO mod_registry (mod_id, mod_version, mod_hash, updated_at) KEY(mod_id, mod_version)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (normalizedHash == null) {
                normalizedHash = getExistingRegisteredHash(conn, normalizedId, normalizedVersion);
            }
            stmt.setString(1, normalizedId);
            stmt.setString(2, normalizedVersion);
            stmt.setString(3, normalizedHash);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.warn("Failed to register mod fingerprint: {}", e.getMessage());
        }
    }

    private String getExistingRegisteredHash(Connection conn, String modId, String modVersion) {
        String sql = "SELECT mod_hash FROM mod_registry WHERE mod_id = ? AND mod_version = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modId);
            stmt.setString(2, modVersion);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String hash = normalizeHash(rs.getString("mod_hash"));
                    if (hash != null) {
                        return hash;
                    }
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private static String normalizeHistoryModToken(String modToken) {
        ModEntry entry = ModEntry.parse(modToken);
        if (entry != null) {
            return entry.toDisplayKey();
        }
        if (modToken == null) {
            return null;
        }
        String trimmed = modToken.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    public Map<String, String> getRegisteredHashes(Set<String> ruleKeys, boolean versioningEnabled) {
        Map<String, String> result = new LinkedHashMap<>();
        if (dataSource == null || ruleKeys == null || ruleKeys.isEmpty()) {
            return result;
        }

        String sql = """
            SELECT mod_hash
            FROM mod_registry
            WHERE mod_id = ? AND mod_version = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String ruleKey : ruleKeys) {
                ModEntry rule = ModEntry.parse(ruleKey);
                if (rule == null) {
                    continue;
                }

                String resolvedKey = rule.toRuleKey(versioningEnabled);
                String version = normalizeVersion(versioningEnabled ? rule.version() : null);

                stmt.setString(1, rule.modId());
                stmt.setString(2, version);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String hash = rs.getString("mod_hash");
                        if (hash != null && !hash.isBlank()) {
                            result.put(resolvedKey, hash);
                        }
                    }
                }

                if (!result.containsKey(resolvedKey) && !version.isEmpty()) {
                    stmt.setString(1, rule.modId());
                    stmt.setString(2, "");
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            String hash = rs.getString("mod_hash");
                            if (hash != null && !hash.isBlank()) {
                                result.put(resolvedKey, hash);
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.warn("Failed to load registered hashes: {}", e.getMessage());
        }

        return result;
    }

    private static String normalizeVersion(String version) {
        if (version == null || version.isBlank() || "null".equalsIgnoreCase(version)) {
            return "";
        }
        return version.toLowerCase(Locale.ROOT);
    }

    private static String normalizeHash(String hash) {
        if (hash == null || hash.isBlank() || "null".equalsIgnoreCase(hash)) {
            return null;
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Player history database closed");
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

    // Logger interface for platform-agnostic logging
    public interface Logger {
        void info(String message, Object... args);
        void warn(String message, Object... args);
        void error(String message, Throwable e);
        void debug(String message);
    }
}