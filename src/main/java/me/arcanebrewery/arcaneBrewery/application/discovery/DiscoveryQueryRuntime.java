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
    private final PersonalNameCache names = new PersonalNameCache(System::currentTimeMillis,2048,32,30_000);

    public DiscoveryQueryRuntime(DiscoveryRepository repository, Executor executor) {
        this.repository = Objects.requireNonNull(repository);
        this.executor = Objects.requireNonNull(executor);
    }

    public CompletableFuture<DrinkFingerprint> resolve(UUID player,String prefix) {
        return CompletableFuture.supplyAsync(()->{
            return RecipeIdResolver.resolve(repository.accessibleIds(player),prefix);
        },executor);
    }
    public CompletableFuture<String> info(UUID player,DrinkFingerprint id) { return CompletableFuture.supplyAsync(()->repository.recordInfo(id,player),executor); }
    public CompletableFuture<java.util.Optional<String>> personalName(UUID producer,DrinkFingerprint id) {
        return names.get(producer,id,executor,()->repository.personalName(producer,id));
    }
    public void invalidatePersonalNames(UUID producer) { names.invalidate(producer); }

    public CompletableFuture<List<OwnedDiscoveryView>> recipes(UUID player,boolean learned,int limit,int offset) {
        return CompletableFuture.supplyAsync(()->repository.personalCatalogByOrigin(player,learned,limit,offset),executor);
    }
    public CompletableFuture<List<OwnedDiscoveryView>> owned(UUID playerId, int limit, int offset) {
        Objects.requireNonNull(playerId);
        return CompletableFuture.supplyAsync(() -> repository.personalCatalog(playerId,limit,offset),executor);
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
