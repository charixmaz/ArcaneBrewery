package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.application.storage.StorageException;
import me.arcanebrewery.arcaneBrewery.storage.jdbc.*;
import me.arcanebrewery.arcaneBrewery.storage.migration.MigrationRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Path is injected; this class has no knowledge of Bukkit's plugin directories. */
public final class SqliteStorageProvider extends JdbcStorageProvider {
    public SqliteStorageProvider(Path databaseFile) {
        super(factory(databaseFile));
        new MigrationRunner(connections, SqliteSchema.migrations()).migrate();
    }

    private static JdbcConnectionFactory factory(Path file) {
        try {
            Path absolute = java.util.Objects.requireNonNull(file).toAbsolutePath().normalize();
            Files.createDirectories(absolute.getParent());
            Class.forName("org.sqlite.JDBC");
            var factory = new SqliteConnectionFactory(absolute);
            try (var connection = factory.open(true);
                 var statement = connection.prepareStatement("PRAGMA journal_mode=WAL");
                 var rows = statement.executeQuery()) {
                if (!rows.next() || !"wal".equalsIgnoreCase(rows.getString(1)))
                    throw new StorageException("SQLite WAL could not be enabled");
            }
            return factory;
        } catch (IOException | ClassNotFoundException | java.sql.SQLException e) {
            throw new StorageException("Unable to initialize SQLite storage", e);
        }
    }
}
