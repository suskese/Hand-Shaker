package me.mklv.handshaker.common.database;

import com.zaxxer.hikari.HikariDataSource;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.utils.CachedValue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class PlayerHistoryDatabase {
    protected static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final long CACHE_TTL_MS = 30_000;

    protected HikariDataSource dataSource;
    protected final Logger logger;
    private final boolean enabled;
    private final DatabaseOptions options;
    private final CachedValue<Map<String, Integer>> modPopularityCache = new CachedValue<>(CACHE_TTL_MS);

    protected PlayerHistoryDatabase(Logger logger, boolean enabled, DatabaseOptions options) {
        this.logger = logger;
        this.enabled = enabled;
        this.options = options != null ? options : DatabaseOptions.defaults();
    }

    protected final void initialize() {
        if (!enabled) {
            return;
        }
        try {
            dataSource = createDataSource();
            createTables();
            afterInitialize();
            logger.info("Player history database initialized at: {}", getDatabaseLocation());
        } catch (Exception e) {
            logger.error("Failed to initialize player history database", e);
            dataSource = null;
        }
    }

    public boolean isEnabled() {
        return enabled && dataSource != null && !dataSource.isClosed();
    }

    public boolean syncPlayerMods(UUID uuid, String playerName, Set<String> currentMods) {
        if (!isEnabled()) {
            return false;
        }

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String upsertPlayer = getUpsertPlayerSql();
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

                Set<String> dbActiveMods = getActiveModsForSync(conn, uuid);

                Set<String> newMods = new HashSet<>(normalizedCurrentMods);
                newMods.removeAll(dbActiveMods);
                Set<String> removedMods = new HashSet<>(dbActiveMods);
                removedMods.removeAll(normalizedCurrentMods);

                if (!newMods.isEmpty()) {
                    String insertMod = getInsertModSql();
                    try (PreparedStatement ps = conn.prepareStatement(insertMod)) {
                        for (String mod : newMods) {
                            ps.setString(1, uuid.toString());
                            ps.setString(2, mod);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

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
                return true;
            } catch (SQLException e) {
                conn.rollback();
                logWarn("Failed to sync player mods: %s", e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            logWarn("Database connection failed: %s", e.getMessage());
            return false;
        }
    }

    public List<ModHistoryEntry> getPlayerHistory(UUID uuid) {
        if (!isEnabled()) {
            return new ArrayList<>();
        }

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
            logWarn("Failed to get player history: %s", e.getMessage());
        }

        return history;
    }

    public Map<String, Integer> getModPopularity() {
        if (!isEnabled()) {
            return new LinkedHashMap<>();
        }

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
            logWarn("Failed to get mod popularity: %s", e.getMessage());
        }

        return popularity;
    }

    public List<PlayerModInfo> getPlayersWithMod(String modName) {
        if (!isEnabled()) {
            return new ArrayList<>();
        }

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
            logWarn("Failed to get players with mod: %s", e.getMessage());
        }

        return players;
    }

    public List<String> getPlayerNames(UUID uuid) {
        if (!isEnabled()) {
            return new ArrayList<>();
        }

        List<String> names = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT current_name FROM player_names WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                names.add(rs.getString("current_name"));
            }
        } catch (SQLException e) {
            logWarn("Failed to get player names: %s", e.getMessage());
        }

        return names;
    }

    public Optional<UUID> getPlayerUuidByName(String playerName) {
        if (!isEnabled() || playerName == null || playerName.isEmpty()) {
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
            logWarn("Failed to get player uuid by name: %s", e.getMessage());
        }
        return Optional.empty();
    }

    public int getUniqueActivePlayers() {
        if (!isEnabled()) {
            return 0;
        }

        String sql = "SELECT COUNT(DISTINCT player_uuid) as player_count FROM mod_history WHERE removed_date IS NULL";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("player_count");
            }
        } catch (SQLException e) {
            logWarn("Failed to get unique active players: %s", e.getMessage());
        }

        return 0;
    }

    public List<PlayerSummaryInfo> getPlayersWithActiveMods() {
        if (!isEnabled()) {
            return new ArrayList<>();
        }

        List<PlayerSummaryInfo> players = new ArrayList<>();
        String sql = """
            SELECT
                mh.player_uuid,
                pn.current_name,
                COUNT(DISTINCT mh.mod_name) AS mod_count
            FROM mod_history mh
            JOIN player_names pn ON mh.player_uuid = pn.uuid
            WHERE mh.removed_date IS NULL
            GROUP BY mh.player_uuid, pn.current_name
            ORDER BY mod_count DESC, pn.current_name ASC
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                players.add(new PlayerSummaryInfo(
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("current_name"),
                    rs.getInt("mod_count")
                ));
            }
        } catch (SQLException e) {
            logWarn("Failed to get players with active mods: %s", e.getMessage());
        }

        return players;
    }

    public void registerModFingerprint(String modToken, boolean versioningEnabled) {
        ModEntry entry = ModEntry.parse(modToken);
        if (entry == null) {
            return;
        }
        registerModFingerprint(entry.modId(), versioningEnabled ? entry.version() : null, entry.hash());
    }

    public void registerModFingerprint(String modId, String modVersion, String modHash) {
        if (!isEnabled() || modId == null || modId.isBlank()) {
            return;
        }

        String normalizedId = modId.toLowerCase(Locale.ROOT);
        String normalizedVersion = normalizeVersion(modVersion);
        String normalizedHash = normalizeHash(modHash);
        String sql = getRegisterModFingerprintSql();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (normalizedHash == null && shouldResolveExistingHash()) {
                normalizedHash = getExistingRegisteredHash(conn, normalizedId, normalizedVersion);
            }
            stmt.setString(1, normalizedId);
            stmt.setString(2, normalizedVersion);
            stmt.setString(3, normalizedHash);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logWarn("Failed to register mod fingerprint: %s", e.getMessage());
        }
    }

    public Map<String, String> getRegisteredHashes(Set<String> ruleKeys, boolean versioningEnabled) {
        Map<String, String> result = new LinkedHashMap<>();
        if (!isEnabled() || ruleKeys == null || ruleKeys.isEmpty()) {
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
            logWarn("Failed to load registered hashes: %s", e.getMessage());
        }

        return result;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Player history database closed");
        }
    }

    /**
     * Deletes mod history rows with a removal date older than {@code days} days.
     * Only removes rows where {@code removed_date IS NOT NULL} (i.e. already-gone mods),
     * never touches currently-active entries.
     *
     * @param days positive number of days; values {@code <= 0} are a no-op
     * @return number of rows deleted, or -1 if the DB is disabled or an error occurred
     */
    public int deleteOldHistory(int days) {
        if (!isEnabled() || days <= 0) {
            return -1;
        }
        long cutoffMs = System.currentTimeMillis() - (long) days * 24L * 60L * 60L * 1000L;
        java.sql.Timestamp cutoff = new java.sql.Timestamp(cutoffMs);
        String sql = "DELETE FROM mod_history WHERE removed_date IS NOT NULL AND removed_date < ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, cutoff);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                modPopularityCache.invalidate();
                logger.info("Purged {} stale mod-history rows older than {} days", deleted, days);
            }
            return deleted;
        } catch (SQLException e) {
            logWarn("Failed to delete old history: %s", e.getMessage());
            return -1;
        }
    }

    public PoolStats getPoolStats() {
        if (!isEnabled() || dataSource == null) {
            return new PoolStats(0, 0, 0, -1);
        }
        var mxBean = dataSource.getHikariPoolMXBean();
        if (mxBean == null) {
            return new PoolStats(0, 0, 0, -1);
        }
        return new PoolStats(
            mxBean.getActiveConnections(),
            mxBean.getIdleConnections(),
            mxBean.getThreadsAwaitingConnection(),
            options.maximumPoolSize()
        );
    }

    protected DatabaseOptions options() {
        return options;
    }

    protected abstract HikariDataSource createDataSource() throws Exception;

    protected abstract void createTables() throws SQLException;

    protected void afterInitialize() throws Exception {
    }

    protected abstract String getDatabaseLocation();

    protected abstract String getUpsertPlayerSql();

    protected abstract String getInsertModSql();

    protected abstract String getRegisterModFingerprintSql();

    protected boolean shouldResolveExistingHash() {
        return false;
    }

    protected String getExistingRegisteredHash(Connection conn, String modId, String modVersion) {
        return null;
    }

    protected Set<String> getActiveModsForSync(Connection conn, UUID uuid) throws SQLException {
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

    protected static String normalizeHistoryModToken(String modToken) {
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

    protected static String normalizeVersion(String version) {
        if (version == null || version.isBlank() || "null".equalsIgnoreCase(version)) {
            return "";
        }
        return version.toLowerCase(Locale.ROOT);
    }

    protected static String normalizeHash(String hash) {
        if (hash == null || hash.isBlank() || "null".equalsIgnoreCase(hash)) {
            return null;
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    protected void logWarn(String format, Object arg) {
        if (format.contains("{}")) {
            logger.warn(format, arg);
            return;
        }
        logger.warn(String.format(format, arg));
    }

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

    public record PlayerSummaryInfo(UUID uuid, String currentName, int modCount) {
    }

    public interface Logger {
        void info(String message, Object... args);
        void warn(String message, Object... args);
        void error(String message, Throwable e);
        void debug(String message);
    }

    public record DatabaseOptions(int maximumPoolSize, long idleTimeoutMs, long maxLifetimeMs) {
        public static DatabaseOptions defaults() {
            return new DatabaseOptions(15, 300_000L, 1_800_000L);
        }

        public static DatabaseOptions of(int maximumPoolSize, long idleTimeoutMs, long maxLifetimeMs) {
            int safePool = Math.max(1, maximumPoolSize);
            long safeIdle = Math.max(10_000L, idleTimeoutMs);
            long safeLifetime = Math.max(30_000L, maxLifetimeMs);
            return new DatabaseOptions(safePool, safeIdle, safeLifetime);
        }
    }

    public record PoolStats(int activeConnections, int idleConnections, int awaitingThreads, int maxPoolSize) {}
}
