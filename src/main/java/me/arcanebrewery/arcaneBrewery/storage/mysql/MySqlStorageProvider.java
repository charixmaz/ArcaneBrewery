package me.arcanebrewery.arcaneBrewery.storage.mysql;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.storage.jdbc.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Opt-in MySQL/MariaDB implementation shape using externally provisioned schema V1.
 * The caller owns its DataSource/driver/pool; this provider never starts an external service.
 */
public final class MySqlStorageProvider extends JdbcStorageProvider {
    public MySqlStorageProvider(DataSource dataSource) {
        super(factory(dataSource));
        int version = schemaVersion();
        if (version != 1) throw new UnsupportedStorageVersionException("MySQL requires externally migrated schema V1");
    }

    private static JdbcConnectionFactory factory(DataSource source) {
        Objects.requireNonNull(source, "dataSource");
        return write -> {
            Connection connection = source.getConnection();
            try {
                String product = connection.getMetaData().getDatabaseProductName();
                if (!"MySQL".equalsIgnoreCase(product) && !"MariaDB".equalsIgnoreCase(product))
                    throw new SQLException("Expected MySQL or MariaDB DataSource");
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                return connection;
            } catch (SQLException e) {
                try { connection.close(); } catch (SQLException close) { e.addSuppressed(close); }
                throw e;
            }
        };
    }
}
