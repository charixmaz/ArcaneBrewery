package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcConnectionFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class SqliteConnectionFactory implements JdbcConnectionFactory {
    private final Path databaseFile;
    public SqliteConnectionFactory(Path databaseFile) {
        this.databaseFile = java.util.Objects.requireNonNull(databaseFile).toAbsolutePath().normalize();
    }

    public Connection open(boolean write) throws SQLException {
        var properties = new Properties();
        properties.setProperty("foreign_keys", "true");
        properties.setProperty("busy_timeout", "10000");
        properties.setProperty("transaction_mode", write ? "IMMEDIATE" : "DEFERRED");
        var connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile, properties);
        try (var statement = connection.prepareStatement("PRAGMA foreign_keys"); var rows = statement.executeQuery()) {
            if (!rows.next() || rows.getInt(1) != 1) throw new SQLException("SQLite foreign keys are not enabled");
            return connection;
        } catch (SQLException e) {
            try { connection.close(); } catch (SQLException close) { e.addSuppressed(close); }
            throw e;
        }
    }
}
