package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import me.arcanebrewery.arcaneBrewery.application.storage.StorageException;
import java.sql.Connection;
import java.sql.SQLException;

public final class JdbcTransactions {
    private JdbcTransactions() {}
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws SQLException; }

    public static <T> T run(JdbcConnectionFactory factory, boolean write, Work<T> work) {
        try (var connection = factory.open(write)) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException e) {
            throw new StorageException("Database operation failed", e);
        }
    }
}
