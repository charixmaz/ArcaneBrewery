package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.storage.migration.MigrationRunner;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-operation connections; no background threads or global connection registry. */
public abstract class JdbcStorageProvider implements StorageProvider {
    protected final JdbcConnectionFactory connections;
    private final DiscoveryRepository repository;
    private final AtomicBoolean closed = new AtomicBoolean();

    protected JdbcStorageProvider(JdbcConnectionFactory factory) {
        this.connections = write -> {
            if (closed.get()) throw new StorageException("Storage provider is closed");
            return factory.open(write);
        };
        repository = new JdbcDiscoveryRepository(connections);
    }
    public DiscoveryRepository discoveries() {
        if (closed.get()) throw new StorageException("Storage provider is closed");
        return repository;
    }
    public int schemaVersion() {
        return JdbcTransactions.run(connections, false, MigrationRunner::currentVersion);
    }
    public void close() { closed.set(true); }
}
