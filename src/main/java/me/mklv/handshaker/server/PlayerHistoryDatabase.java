package me.mklv.handshaker.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PlayerHistoryDatabase {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private HikariDataSource dataSource;
    private final File dbFile;

    public PlayerHistoryDatabase() {
        this.dbFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "hand-shaker-history.db");
        initialize();
    }

    private void initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setMaximumPoolSize(5);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);
            
            dataSource = new HikariDataSource(config);
            
            createTables();
            HandShakerServer.LOGGER.info("Player history database initialized at: {}", dbFile.getAbsolutePath());
        } catch (Exception e) {
            HandShakerServer.LOGGER.error("Failed to initialize player history database", e);
        }
    }

    private void createTables() {
        String createPlayerNames = """
            CREATE TABLE IF NOT EXISTS player_names (
                uuid TEXT NOT NULL,
                name TEXT NOT NULL,
                first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (uuid, name)
            )
        """;

        String createModHistory = """
            CREATE TABLE IF NOT EXISTS mod_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                mod_name TEXT NOT NULL,
                added_date TIMESTAMP NOT NULL,
                removed_date TIMESTAMP,
                FOREIGN KEY (player_uuid) REFERENCES player_names(uuid)
            )
        """;

        String createIndexes = """
            CREATE INDEX IF NOT EXISTS idx_mod_history_uuid ON mod_history(player_uuid);
            CREATE INDEX IF NOT EXISTS idx_mod_history_mod ON mod_history(mod_name);
            CREATE INDEX IF NOT EXISTS idx_player_names_uuid ON player_names(uuid);
        """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayerNames);
            stmt.execute(createModHistory);
            stmt.execute(createIndexes);
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to create database tables", e);
        }
    }

    /**
     * Update or insert player name record
     */
    public void updatePlayerName(UUID uuid, String name) {
        String sql = """
            INSERT INTO player_names (uuid, name, first_seen, last_seen)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT(uuid, name) DO UPDATE SET last_seen = CURRENT_TIMESTAMP
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to update player name", e);
        }
    }

    /**
     * Track a new mod for a player or update if already exists
     */
    public void trackMod(UUID uuid, String modName, Set<String> currentMods) {
        // First, check if this mod is currently tracked as active
        String checkSql = "SELECT id FROM mod_history WHERE player_uuid = ? AND mod_name = ? AND removed_date IS NULL";
        
        try (Connection conn = dataSource.getConnection()) {
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setString(1, uuid.toString());
                checkStmt.setString(2, modName);
                ResultSet rs = checkStmt.executeQuery();
                exists = rs.next();
            }

            if (!exists) {
                // Insert new mod entry
                String insertSql = "INSERT INTO mod_history (player_uuid, mod_name, added_date) VALUES (?, ?, CURRENT_TIMESTAMP)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.setString(2, modName);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to track mod", e);
        }
    }

    /**
     * Mark a mod as removed for a player
     */
    public void removeMod(UUID uuid, String modName) {
        String sql = "UPDATE mod_history SET removed_date = CURRENT_TIMESTAMP WHERE player_uuid = ? AND mod_name = ? AND removed_date IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, modName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to remove mod", e);
        }
    }

    /**
     * Sync player's current mod list with database
     */
    public void syncPlayerMods(UUID uuid, String playerName, Set<String> currentMods) {
        updatePlayerName(uuid, playerName);

        // Get currently active mods from database
        Set<String> dbMods = getActiveMods(uuid);

        // Find new mods (in currentMods but not in dbMods)
        Set<String> newMods = new HashSet<>(currentMods);
        newMods.removeAll(dbMods);

        // Find removed mods (in dbMods but not in currentMods)
        Set<String> removedMods = new HashSet<>(dbMods);
        removedMods.removeAll(currentMods);

        // Track new mods
        for (String mod : newMods) {
            trackMod(uuid, mod, currentMods);
        }

        // Remove old mods
        for (String mod : removedMods) {
            removeMod(uuid, mod);
        }
    }

    /**
     * Get currently active mods for a player
     */
    private Set<String> getActiveMods(UUID uuid) {
        Set<String> mods = new HashSet<>();
        String sql = "SELECT mod_name FROM mod_history WHERE player_uuid = ? AND removed_date IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                mods.add(rs.getString("mod_name"));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to get active mods", e);
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
                String modName = rs.getString("mod_name");
                Timestamp addedDate = rs.getTimestamp("added_date");
                Timestamp removedDate = rs.getTimestamp("removed_date");
                
                history.add(new ModHistoryEntry(
                    modName,
                    addedDate != null ? addedDate.toLocalDateTime() : null,
                    removedDate != null ? removedDate.toLocalDateTime() : null
                ));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to get player history", e);
        }

        return history;
    }

    /**
     * Get mod popularity (how many unique players have used each mod)
     */
    public Map<String, Integer> getModPopularity() {
        Map<String, Integer> popularity = new LinkedHashMap<>();
        String sql = """
            SELECT mod_name, COUNT(DISTINCT player_uuid) as player_count 
            FROM mod_history 
            GROUP BY mod_name 
            ORDER BY player_count DESC, mod_name ASC
        """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                popularity.put(rs.getString("mod_name"), rs.getInt("player_count"));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to get mod popularity", e);
        }

        return popularity;
    }

    /**
     * Get list of players who have used a specific mod
     */
    public List<PlayerModInfo> getPlayersWithMod(String modName) {
        List<PlayerModInfo> players = new ArrayList<>();
        String sql = """
            SELECT DISTINCT 
                mh.player_uuid,
                (SELECT name FROM player_names WHERE uuid = mh.player_uuid ORDER BY last_seen DESC LIMIT 1) as current_name,
                MIN(mh.added_date) as first_seen,
                MAX(CASE WHEN mh.removed_date IS NULL THEN 1 ELSE 0 END) as is_active
            FROM mod_history mh
            WHERE mh.mod_name = ?
            GROUP BY mh.player_uuid
            ORDER BY is_active DESC, first_seen DESC
        """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String uuidStr = rs.getString("player_uuid");
                String name = rs.getString("current_name");
                Timestamp firstSeen = rs.getTimestamp("first_seen");
                boolean isActive = rs.getInt("is_active") == 1;
                
                players.add(new PlayerModInfo(
                    UUID.fromString(uuidStr),
                    name,
                    firstSeen != null ? firstSeen.toLocalDateTime() : null,
                    isActive
                ));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to get players with mod", e);
        }

        return players;
    }

    /**
     * Get all known names for a player UUID
     */
    public List<String> getPlayerNames(UUID uuid) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM player_names WHERE uuid = ? ORDER BY last_seen DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            HandShakerServer.LOGGER.error("Failed to get player names", e);
        }

        return names;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            HandShakerServer.LOGGER.info("Player history database closed");
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
