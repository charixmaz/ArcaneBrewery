package me.arcanebrewery.arcaneBrewery.application.storage;

public interface StorageProvider extends AutoCloseable {
    DiscoveryRepository discoveries();
    int schemaVersion();
    @Override void close();
}
