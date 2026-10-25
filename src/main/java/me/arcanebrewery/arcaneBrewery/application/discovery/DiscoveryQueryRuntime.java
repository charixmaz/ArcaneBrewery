package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.application.storage.DiscoveryRepository;
import me.arcanebrewery.arcaneBrewery.domain.discovery.RecipeKnowledge;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Async player-facing discovery queries. Trusted aggregates are projected before returning. */
public final class DiscoveryQueryRuntime {
    private final DiscoveryRepository repository;
    private final Executor executor;

    public DiscoveryQueryRuntime(DiscoveryRepository repository, Executor executor) {
        this.repository = Objects.requireNonNull(repository);
        this.executor = Objects.requireNonNull(executor);
    }

    public CompletableFuture<List<OwnedDiscoveryView>> owned(UUID playerId, int limit, int offset) {
        Objects.requireNonNull(playerId);
        return CompletableFuture.supplyAsync(() -> repository.findOwnedBy(playerId, limit, offset)
                .stream()
                .map(stored -> OwnedDiscoveryView.from(stored.discovery()))
                .toList(), executor);
    }

    public CompletableFuture<List<RecipeListItem>> publicDiscoveries(int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> repository.findPublicSummaries(limit, offset)
                .stream().map(d -> new RecipeListItem(d.displayName().value(), d.fingerprint())).toList(), executor);
    }

    public CompletableFuture<RecipeKnowledge> knowledge(UUID playerId, DrinkFingerprint fingerprint) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(fingerprint);
        return CompletableFuture.supplyAsync(() -> repository.knowledgeFor(fingerprint, playerId), executor);
    }
}
