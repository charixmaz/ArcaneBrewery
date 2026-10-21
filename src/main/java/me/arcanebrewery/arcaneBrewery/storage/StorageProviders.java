package me.arcanebrewery.arcaneBrewery.storage;

import me.arcanebrewery.arcaneBrewery.application.storage.StorageProvider;
import me.arcanebrewery.arcaneBrewery.storage.sqlite.SqliteStorageProvider;
import java.nio.file.Path;

public final class StorageProviders {
    private StorageProviders() {}
    public static StorageProvider openDefault(Path dataDirectory) {
        return new SqliteStorageProvider(dataDirectory.resolve("arcanebrewery.db"));
    }
}
