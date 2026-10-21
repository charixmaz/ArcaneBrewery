package me.arcanebrewery.arcaneBrewery.application.storage;

public final class CorruptRecordException extends StorageException {
    public CorruptRecordException(String message) { super(message); }
    public CorruptRecordException(String message, Throwable cause) { super(message, cause); }
}
