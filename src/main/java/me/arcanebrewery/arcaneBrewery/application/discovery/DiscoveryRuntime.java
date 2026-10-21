package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.application.storage.RegistrationResult;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Executes blocking discovery persistence away from the caller thread. */
public final class DiscoveryRuntime {
    private final DiscoveryRegistrationService registrations;
    private final Executor executor;

    public DiscoveryRuntime(DiscoveryRegistrationService registrations, Executor executor) {
        this.registrations = Objects.requireNonNull(registrations, "registrations");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletableFuture<RegistrationResult> register(
            UUID playerId, ProcessProfile process, Instant completedAt) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(completedAt, "completedAt");
        return CompletableFuture.supplyAsync(
                () -> registrations.register(playerId, process, completedAt), executor);
    }
}
