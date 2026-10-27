package me.arcanebrewery.arcaneBrewery.application.discovery;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;
/** All persistence is executor-owned; caller supplies a server-thread possession snapshot, never preview items. */
public final class MaterialDiscoveryRuntime {
    private final MaterialDiscoveryRepository repository;private final Executor worker;
    public MaterialDiscoveryRuntime(MaterialDiscoveryRepository repository,Executor worker) {this.repository=Objects.requireNonNull(repository);this.worker=Objects.requireNonNull(worker);}
    public CompletableFuture<MaterialDiscoveryRepository.Update> obtain(UUID player,Set<MaterialKey> keys) {
        Set<MaterialKey> snapshot=Set.copyOf(keys);
        if(snapshot.size()>128) throw new IllegalArgumentException("possession snapshot exceeds catalog bound");
        return CompletableFuture.supplyAsync(()->repository.obtain(player,snapshot,Instant.now()),worker);
    }
}
