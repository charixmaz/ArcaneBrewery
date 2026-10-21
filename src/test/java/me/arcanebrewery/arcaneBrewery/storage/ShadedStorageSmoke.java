package me.arcanebrewery.arcaneBrewery.storage;

import java.nio.file.Path;
import java.sql.DriverManager;

/** Executed against the packaged shaded JAR after Maven verification; test classes supply only this entry point. */
public final class ShadedStorageSmoke {
    private ShadedStorageSmoke() {}
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        try (var storage = StorageProviders.openDefault(directory)) {
            if (storage.schemaVersion() != 1) throw new IllegalStateException("Unexpected schema version");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("arcanebrewery.db"));
             var statement = connection.prepareStatement("SELECT sqlite_version()");
             var rows = statement.executeQuery()) {
            if (!rows.next()) throw new IllegalStateException("No SQLite version");
            System.out.println("Packaged SQLite " + rows.getString(1) + "; schema V1; storage smoke passed");
        }
    }
}
