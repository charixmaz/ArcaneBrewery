package me.arcanebrewery.arcaneBrewery.application.storage;

public final class OptimisticLockException extends StorageException {
    public OptimisticLockException() {
        super("Discovery revision or immutable state no longer matches; reload before updating");
    }
}
