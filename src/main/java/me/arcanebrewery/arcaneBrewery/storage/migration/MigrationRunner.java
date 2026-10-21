package me.arcanebrewery.arcaneBrewery.storage.migration;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.storage.jdbc.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** SQLite transactional DDL migrations. Never use this runner for MySQL's implicit-commit DDL. */
public final class MigrationRunner {
    private final JdbcConnectionFactory connections;
    private final List<SqlMigration> migrations;

    public MigrationRunner(JdbcConnectionFactory connections, List<SqlMigration> migrations) {
        this.connections = java.util.Objects.requireNonNull(connections);
        this.migrations = List.copyOf(migrations);
        for (int i = 0; i < this.migrations.size(); i++) {
            if (this.migrations.get(i).version() != i + 1)
                throw new IllegalArgumentException("migrations must be consecutive and ordered from V1");
        }
    }

    public int migrate() {
        return JdbcTransactions.run(connections, true, connection -> {
            try (var statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, checksum TEXT NOT NULL)")) {
                statement.execute();
            }
            int applied = 0;
            try (var statement = connection.prepareStatement("SELECT version, checksum FROM schema_migrations ORDER BY version");
                 var rows = statement.executeQuery()) {
                while (rows.next()) {
                    int version = rows.getInt(1);
                    if (version > migrations.size())
                        throw new UnsupportedStorageVersionException("Database schema is newer than this application");
                    if (version != applied + 1 || !migrations.get(applied).checksum().equals(rows.getString(2)))
                        throw new CorruptRecordException("Migration history has a gap or checksum mismatch");
                    applied++;
                }
            }
            for (int i = applied; i < migrations.size(); i++) {
                var migration = migrations.get(i);
                for (String sql : migration.statements()) {
                    try (var statement = connection.prepareStatement(sql)) { statement.execute(); }
                }
                try (var statement = connection.prepareStatement("INSERT INTO schema_migrations(version, checksum) VALUES (?, ?)")) {
                    statement.setInt(1, migration.version()); statement.setString(2, migration.checksum());
                    statement.executeUpdate();
                }
            }
            return migrations.size();
        });
    }

    public static int currentVersion(Connection connection) throws SQLException {
        int version = 0;
        try (var statement = connection.prepareStatement("SELECT version FROM schema_migrations ORDER BY version");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                if (rows.getInt(1) != version + 1) throw new CorruptRecordException("Migration history has a gap");
                version++;
            }
        }
        return version;
    }
}
