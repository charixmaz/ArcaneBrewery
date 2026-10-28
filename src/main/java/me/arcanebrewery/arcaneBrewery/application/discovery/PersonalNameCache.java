package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded cache, including negative results and shared in-flight lookups. */
final class PersonalNameCache {
    private record Key(UUID producer, DrinkFingerprint id) {}
    private static final class Entry {
        final CompletableFuture<Optional<String>> result = new CompletableFuture<>();
        long expires = Long.MAX_VALUE;
    }
    private final LinkedHashMap<Key,Entry> entries = new LinkedHashMap<>();
    private final LongSupplier clock;
    private final int capacity, concurrency;
    private final long ttl;
    PersonalNameCache(LongSupplier clock, int capacity, int concurrency, long ttl) {
        this.clock=clock; this.capacity=capacity; this.concurrency=concurrency; this.ttl=ttl;
    }
    CompletableFuture<Optional<String>> get(UUID producer, DrinkFingerprint id, Executor executor,
                                            Supplier<Optional<String>> lookup) {
        Key key=new Key(producer,id); Entry entry;
        synchronized(this) {
            entries.entrySet().removeIf(e -> e.getValue().result.isDone() && e.getValue().expires<=clock.getAsLong());
            entry=entries.get(key);
            if(entry!=null) return entry.result.copy();
            if(entries.values().stream().filter(e -> !e.result.isDone()).count()>=concurrency)
                return CompletableFuture.completedFuture(Optional.empty());
            if(entries.size()>=capacity) {
                var completed=entries.entrySet().iterator();
                while(completed.hasNext()) { if(completed.next().getValue().result.isDone()) { completed.remove(); break; } }
                if(entries.size()>=capacity) return CompletableFuture.completedFuture(Optional.empty());
            }
            entry=new Entry(); entries.put(key,entry);
        }
        Entry pending=entry;
        try {
            executor.execute(() -> {
                try {
                    var name=Objects.requireNonNull(lookup.get());
                    synchronized(this) { pending.expires=clock.getAsLong()+ttl; }
                    pending.result.complete(name);
                } catch(RuntimeException failure) { fail(key,pending,failure); }
            });
        } catch(RuntimeException rejected) { fail(key,pending,rejected); }
        return pending.result.copy();
    }
    private synchronized void fail(Key key, Entry entry, RuntimeException failure) {
        entries.remove(key,entry); entry.result.completeExceptionally(failure);
    }
    synchronized void invalidate(UUID producer) {
        var it=entries.entrySet().iterator();
        while(it.hasNext()) {
            var entry=it.next();
            if(entry.getKey().producer().equals(producer)) {
                it.remove();
                entry.getValue().result.completeExceptionally(new CancellationException("name changed; refresh again"));
            }
        }
    }
}
