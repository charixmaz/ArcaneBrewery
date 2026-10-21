package me.arcanebrewery.arcaneBrewery.application.storage;

/** Messages never include process payloads or names. Callers should not log secret JDBC parameters. */
public class StorageException extends RuntimeException {
    public StorageException(String message) { super(message); }
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
