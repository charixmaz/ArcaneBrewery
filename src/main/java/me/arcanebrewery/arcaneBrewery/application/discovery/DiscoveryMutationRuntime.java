package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

public final class DiscoveryMutationRuntime {
    private final DiscoveryRepository repository;
    private final Executor executor;

    public DiscoveryMutationRuntime(DiscoveryRepository repository, Executor executor) {
        this.repository = Objects.requireNonNull(repository);
        this.executor = Objects.requireNonNull(executor);
    }

    public CompletableFuture<DiscoveryMutationResult> rename(
            UUID actor, DrinkFingerprint fingerprint, String name) {
        return mutate(actor, fingerprint, discovery -> discovery.rename(actor, name));
    }

    public CompletableFuture<DiscoveryMutationResult> visibility(
            UUID actor, DrinkFingerprint fingerprint, DiscoveryVisibility visibility) {
        return mutate(actor, fingerprint, discovery -> discovery.changeVisibility(actor, visibility));
    }

    public CompletableFuture<DiscoveryMutationResult> shareFull(
            UUID actor, DrinkFingerprint fingerprint, UUID recipient) {
        return mutate(actor, fingerprint, discovery -> discovery.shareFull(actor, recipient));
    }

    public CompletableFuture<DiscoveryMutationResult> sharePartial(
            UUID actor, DrinkFingerprint fingerprint, UUID recipient, Set<ProcessField> fields) {
        Set<ProcessField> safeFields = Set.copyOf(fields);
        return mutate(actor, fingerprint, discovery -> discovery.sharePartial(actor, recipient, safeFields));
    }

    public CompletableFuture<DiscoveryMutationResult> revoke(
            UUID actor, DrinkFingerprint fingerprint, UUID recipient) {
        return mutate(actor, fingerprint, discovery -> discovery.revoke(actor, recipient));
    }

    private CompletableFuture<DiscoveryMutationResult> mutate(
            UUID actor, DrinkFingerprint fingerprint, UnaryOperator<PlayerDiscovery> mutation) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(mutation);
        return CompletableFuture.supplyAsync(() -> attempt(actor, fingerprint, mutation, true), executor);
    }

    private DiscoveryMutationResult attempt(
            UUID actor, DrinkFingerprint fingerprint, UnaryOperator<PlayerDiscovery> mutation, boolean retry) {
        StoredDiscovery stored = repository.findForPlayer(fingerprint, actor)
                .orElseThrow(() -> new IllegalArgumentException("discovery not found"));
        if (!stored.discovery().discovererId().equals(actor))
            throw new SecurityException("only the original discoverer may change this discovery");

        PlayerDiscovery updated = mutation.apply(stored.discovery());
        try {
            StoredDiscovery saved = repository.update(stored.revision(), updated);
            return new DiscoveryMutationResult(
                    saved.discovery().fingerprint(),
                    saved.discovery().displayName(),
                    saved.discovery().visibility(),
                    saved.revision()
            );
        } catch (OptimisticLockException stale) {
            if (!retry) throw stale;
            return attempt(actor, fingerprint, mutation, false);
        }
    }
}
