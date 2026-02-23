package me.mklv.handshaker.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class H2PlayerHistoryDatabase extends PlayerHistoryDatabase {
    private final File dbFile;

    public H2PlayerHistoryDatabase(File configDir, Logger logger) {
        super(logger, true);
        this.dbFile = new File(configDir, "hand-shaker-history");
        initialize();
    }

    @Override
    protected HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:" + dbFile.getAbsolutePath() + ";MODE=MySQL;AUTO_SERVER=TRUE");
        config.setDriverClassName("org.h2.Driver");
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        return new HikariDataSource(config);
    }

    @Override
    protected void createTables() throws SQLException {
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
        }
    }

    @Override
    protected void afterInitialize() throws SQLException {
        migrateSchemaIfNeeded();
    }

    @Override
    protected String getDatabaseLocation() {
        return dbFile.getAbsolutePath();
    }

    @Override
    protected String getUpsertPlayerSql() {
        return """
            MERGE INTO player_names (uuid, current_name, first_seen, last_seen) KEY(uuid)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
    }

    @Override
    protected String getInsertModSql() {
        return """
            INSERT INTO mod_history (player_uuid, mod_name, added_date, removed_date)
            VALUES (?, ?, CURRENT_TIMESTAMP, NULL)
            """;
    }

    @Override
    protected String getRegisterModFingerprintSql() {
        return """
            MERGE INTO mod_registry (mod_id, mod_version, mod_hash, updated_at) KEY(mod_id, mod_version)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            """;
    }

    @Override
    protected boolean shouldResolveExistingHash() {
        return true;
    }

    @Override
    protected String getExistingRegisteredHash(Connection conn, String modId, String modVersion) {
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

    private void migrateSchemaIfNeeded() throws SQLException {
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

            if (!hasOldNameColumn && !hasNewCurrentNameColumn) {
                logger.debug("Fresh database detected - schema is correct");
                return;
            }

            if (hasNewCurrentNameColumn && !hasOldNameColumn) {
                logger.debug("Database already has correct schema");
                return;
            }

            if (hasOldNameColumn && !hasNewCurrentNameColumn) {
                logger.info("Migrating database schema from old to new format");

                try (Connection migConn = dataSource.getConnection()) {
                    migConn.setAutoCommit(false);
                    try (Statement migStmt = migConn.createStatement()) {
                        migStmt.execute("ALTER TABLE player_names RENAME TO player_names_old");

                        migStmt.execute("""
                                CREATE TABLE player_names (
                                    uuid TEXT PRIMARY KEY,
                                    current_name TEXT NOT NULL,
                                    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                                )""");

                        migStmt.execute("""
                                INSERT INTO player_names (uuid, current_name, first_seen, last_seen)
                                SELECT uuid, name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM player_names_old""");

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
        }
    }
}
