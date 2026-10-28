package me.arcanebrewery.arcaneBrewery.application.aging;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

/** Keeps lanes busy until persistence completes and suppresses deliveries to closed sessions. */
public final class HarvestSaveGate<T> implements AutoCloseable {
    private final Set<Integer> pending=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed=new AtomicBoolean();
    public boolean pending(int lane) { return pending.contains(lane); }
    public boolean submit(int lane, Supplier<CompletableFuture<T>> save, Executor server,
                          BiConsumer<T,Throwable> completion) {
        if(closed.get() || !pending.add(lane)) return false;
        CompletableFuture<T> saved;
        try { saved=save.get(); }
        catch(RuntimeException rejected) { saved=CompletableFuture.failedFuture(rejected); }
        saved.whenComplete((value,failure)->{
            if(closed.get()) { pending.remove(lane); return; }
            try {
                server.execute(()->{
                    try { if(!closed.get()) completion.accept(value,failure); }
                    finally { pending.remove(lane); }
                });
            } catch(RuntimeException rejected) { pending.remove(lane); } // Original lane remains recoverable.
        });
        return true;
    }
    public void close() { closed.set(true); pending.clear(); }
}
