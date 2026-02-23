package me.mklv.handshaker.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLitePlayerHistoryDatabase extends PlayerHistoryDatabase {
    private final File dbFile;

    public SQLitePlayerHistoryDatabase(File dataFolder, Logger logger, boolean enabled) {
        super(logger, enabled);
        this.dbFile = new File(dataFolder, "hand-shaker-history.db");
        initialize();
    }

    @Override
    protected HikariDataSource createDataSource() {
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
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
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
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
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_history_active ON mod_history(removed_date) WHERE removed_date IS NULL");
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
    protected String getDatabaseLocation() {
        return dbFile.getAbsolutePath();
    }

    @Override
    protected String getUpsertPlayerSql() {
        return """
            INSERT INTO player_names (uuid, current_name, first_seen, last_seen)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT(uuid) DO UPDATE SET
                current_name = excluded.current_name,
                last_seen = CURRENT_TIMESTAMP
            """;
    }

    @Override
    protected String getInsertModSql() {
        return """
            INSERT INTO mod_history (player_uuid, mod_name, added_date, removed_date)
            VALUES (?, ?, CURRENT_TIMESTAMP, NULL)
            ON CONFLICT(player_uuid, mod_name, added_date) DO NOTHING
            """;
    }

    @Override
    protected String getRegisterModFingerprintSql() {
        return """
            INSERT INTO mod_registry (mod_id, mod_version, mod_hash, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(mod_id, mod_version) DO UPDATE SET
              mod_hash = COALESCE(excluded.mod_hash, mod_registry.mod_hash),
              updated_at = CURRENT_TIMESTAMP
            """;
    }
}
