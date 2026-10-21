package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface JdbcConnectionFactory {
    Connection open(boolean write) throws SQLException;
}
