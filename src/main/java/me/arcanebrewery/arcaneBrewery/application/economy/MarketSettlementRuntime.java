package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/** Runs journal/economy work on a worker, and inventory operations on the server
 * executor. Never wait/join the returned future on the server thread. */
public final class MarketSettlementRuntime implements AutoCloseable {
    private final Executor worker;
    private final Executor server;
    private final BooleanSupplier isServerThread;
    private final LongSupplier clock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();

    public MarketSettlementRuntime(Executor worker, Executor server, BooleanSupplier isServerThread, LongSupplier clock) {
        this.worker = Objects.requireNonNull(worker);
        this.server = Objects.requireNonNull(server);
        this.isServerThread = Objects.requireNonNull(isServerThread);
        this.clock = Objects.requireNonNull(clock);
    }

    public CompletableFuture<MarketSettlementResult> settleSale(
            MarketTransactionJournal journal, MarketAssetPort assets, EconomyPort economy,
            UUID player, MarketAsset asset, MarketQuote quote, UUID transaction) {
        Objects.requireNonNull(journal); Objects.requireNonNull(assets); Objects.requireNonNull(economy);
        return submit(worker, () -> {
            if (isServerThread.getAsBoolean()) throw new IllegalStateException("market storage requires a worker thread");
            var serverAssets = new MarketAssetPort() {
                public boolean removeForSale(UUID p, MarketAsset a, UUID id) {
                    return inventory(() -> assets.removeForSale(p, a, id));
                }
                public boolean restoreAfterFailedSale(UUID p, MarketAsset a, UUID id) {
                    return inventory(() -> assets.restoreAfterFailedSale(p, a, id));
                }
                public void finalizeSale(UUID p, MarketAsset a, UUID id) {
                    inventory(() -> { assets.finalizeSale(p, a, id); return true; });
                }
            };
            return new MarketSettlementService(journal, serverAssets, economy)
                    .settleSale(player, asset, quote, transaction, clock.getAsLong());
        });
    }

    /** Built-in escrow port already confines its individual inventory calls to the server. */
    public CompletableFuture<MarketSettlementResult> settleWorkerSale(
            MarketTransactionJournal journal, MarketAssetPort assets, EconomyPort economy,
            UUID player, MarketAsset asset, MarketQuote quote, UUID transaction) {
        return submit(worker, () -> {
            if(isServerThread.getAsBoolean()) throw new IllegalStateException("market storage requires a worker thread");
            return new MarketSettlementService(journal,assets,economy)
                    .settleSale(player,asset,quote,transaction,clock.getAsLong());
        });
    }

    public <T> T inventory(Supplier<T> operation) {
        if(isServerThread.getAsBoolean()) throw new IllegalStateException("only a market worker may wait for inventory handoff");
        return submit(server, () -> {
            if (!isServerThread.getAsBoolean()) throw new IllegalStateException("market inventory requires the server thread");
            return operation.get();
        }).join(); // Only the worker waits. Shutdown completes pending handoffs.
    }

    private <T> CompletableFuture<T> submit(Executor executor, Supplier<T> operation) {
        var result = new CompletableFuture<T>();
        pending.add(result);
        result.whenComplete((value, failure) -> pending.remove(result));
        if (closed.get()) result.completeExceptionally(new IllegalStateException("market runtime closed"));
        else try {
            executor.execute(() -> {
                if (result.isDone()) return;
                if (closed.get()) { result.completeExceptionally(new IllegalStateException("market runtime closed")); return; }
                try { result.complete(operation.get()); }
                catch (RuntimeException | LinkageError failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException rejected) { result.completeExceptionally(rejected); }
        return result;
    }

    public void close() {
        closed.set(true);
        for (var future : pending) future.completeExceptionally(new IllegalStateException("market runtime closed; reconcile pending IDs"));
    }
}
