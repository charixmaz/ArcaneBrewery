package me.arcanebrewery.arcaneBrewery.application.economy;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketSettlementRuntimeTest {
    static final UUID PLAYER = UUID.randomUUID();
    static MarketAsset asset() { return MarketAsset.from("b".repeat(64), new DrinkValue(1,100,50,30,20,10)); }
    static MarketQuote quote(MarketAsset asset) {
        return new MarketQuote(1,UUID.randomUUID(),asset.fingerprint(),asset.token(),MarketSide.PLAYER_SELLS,
                asset.value().units(),75.25,1_000,31_000);
    }
    static final class Journal implements MarketTransactionJournal {
        final List<String> threads = new CopyOnWriteArrayList<>();
        int begins, commits, rejects;
        public boolean begin(MarketTransaction transaction) { begins++; threads.add(Thread.currentThread().getName()); return true; }
        public void commit(UUID id) { commits++; threads.add(Thread.currentThread().getName()); }
        public void reject(UUID id,String reason) { rejects++; threads.add(Thread.currentThread().getName()); }
    }
    static final class Assets implements MarketAssetPort {
        final List<String> threads = new CopyOnWriteArrayList<>();
        int removes, restores;
        public boolean removeForSale(UUID p,MarketAsset a,UUID id) { removes++; threads.add(Thread.currentThread().getName()); return true; }
        public boolean restoreAfterFailedSale(UUID p,MarketAsset a,UUID id) { restores++; threads.add(Thread.currentThread().getName()); return true; }
    }
    static EconomyPort economy(java.util.function.BiFunction<BigDecimal,UUID,Boolean> deposit) {
        return new EconomyPort() {
            public boolean canAfford(UUID p,BigDecimal a) { return true; }
            public boolean withdraw(UUID p,BigDecimal a,UUID id) { return true; }
            public boolean deposit(UUID p,BigDecimal a,UUID id) { return deposit.apply(a,id); }
        };
    }
    @Test void storageAndEconomyRunOffServerWhileInventoryRunsOnServer() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor(r -> new Thread(r,"market-worker"));
             var server = Executors.newSingleThreadExecutor(r -> new Thread(r,"test-server"));
             var runtime = new MarketSettlementRuntime(worker,server,() -> Thread.currentThread().getName().equals("test-server"),() -> 2_000)) {
            var journal = new Journal(); var assets = new Assets(); var asset = asset(); UUID id = UUID.randomUUID();
            var seen = new AtomicReference<UUID>();
            var result = runtime.settleSale(journal,assets,economy((amount,tx) -> {
                assertEquals("market-worker",Thread.currentThread().getName());
                assertEquals(new BigDecimal("75.25"),amount); seen.set(tx); return true;
            }),PLAYER,asset,quote(asset),id).get(3,TimeUnit.SECONDS);
            assertEquals(MarketSettlementResult.Status.COMMITTED,result.status());
            assertEquals(id,seen.get()); assertEquals(List.of("test-server"),assets.threads);
            assertEquals(List.of("market-worker","market-worker"),journal.threads);
        }
    }
    @Test void missingEconomyRejectsAndRestoresOnServer() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor();
             var server = Executors.newSingleThreadExecutor(r -> new Thread(r,"test-server"));
             var runtime = new MarketSettlementRuntime(worker,server,() -> Thread.currentThread().getName().equals("test-server"),() -> 2_000)) {
            var journal = new Journal(); var assets = new Assets(); var asset = asset();
            var result = runtime.settleSale(journal,assets,UnavailableEconomyPort.INSTANCE,PLAYER,asset,quote(asset),UUID.randomUUID())
                    .get(3,TimeUnit.SECONDS);
            assertEquals(MarketSettlementResult.Status.REJECTED,result.status());
            assertEquals(1,assets.restores); assertEquals(1,journal.rejects);
            assertEquals(List.of("test-server","test-server"),assets.threads);
        }
    }
    @Test void uncertainWriteDoesNotRestoreOrRejectReservation() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor();
             var server = Executors.newSingleThreadExecutor(r -> new Thread(r,"test-server"));
             var runtime = new MarketSettlementRuntime(worker,server,() -> Thread.currentThread().getName().equals("test-server"),() -> 2_000)) {
            var journal = new Journal(); var assets = new Assets(); var asset = asset();
            var result = runtime.settleSale(journal,assets,economy((a,id) -> { throw new EconomyOperationUncertainException(new IllegalStateException()); }),
                    PLAYER,asset,quote(asset),UUID.randomUUID());
            assertInstanceOf(EconomyOperationUncertainException.class,
                    assertThrows(ExecutionException.class,() -> result.get(3,TimeUnit.SECONDS)).getCause());
            assertEquals(1,journal.begins); assertEquals(0,journal.commits); assertEquals(0,journal.rejects);
            assertEquals(1,assets.removes); assertEquals(0,assets.restores);
        }
    }
    @Test void misconfiguredWorkerCannotRunJournalOnServerThread() {
        try (var runtime = new MarketSettlementRuntime(Runnable::run,Runnable::run,() -> true,() -> 2_000)) {
            var journal = new Journal(); var asset = asset();
            var result = runtime.settleSale(journal,new Assets(),UnavailableEconomyPort.INSTANCE,PLAYER,asset,quote(asset),UUID.randomUUID());
            assertThrows(CompletionException.class,result::join); assertEquals(0,journal.begins);
        }
    }
    @Test void shutdownReleasesWaitingWorkerAndPreventsQueuedInventoryMutation() throws Exception {
        try (var worker = Executors.newSingleThreadExecutor()) {
            var queued = new AtomicReference<Runnable>(); var submitted = new CountDownLatch(1);
            var runtime = new MarketSettlementRuntime(worker,task -> { queued.set(task); submitted.countDown(); },() -> false,() -> 2_000);
            var assets = new Assets(); var asset = asset();
            var result = runtime.settleSale(new Journal(),assets,UnavailableEconomyPort.INSTANCE,PLAYER,asset,quote(asset),UUID.randomUUID());
            assertTrue(submitted.await(3,TimeUnit.SECONDS)); runtime.close();
            assertThrows(CompletionException.class,result::join);
            queued.get().run(); assertEquals(0,assets.removes);
            assertThrows(CompletionException.class,() -> runtime.settleSale(new Journal(),assets,UnavailableEconomyPort.INSTANCE,
                    PLAYER,asset,quote(asset),UUID.randomUUID()).join());
        }
    }
    @Test void rejectedSchedulingFailsWithoutTouchingAssets() {
        try (var runtime = new MarketSettlementRuntime(task -> { throw new RejectedExecutionException(); },Runnable::run,() -> false,() -> 2_000)) {
            var assets = new Assets(); var asset = asset();
            var result = runtime.settleSale(new Journal(),assets,UnavailableEconomyPort.INSTANCE,PLAYER,asset,quote(asset),UUID.randomUUID());
            assertThrows(CompletionException.class,result::join); assertEquals(0,assets.removes);
        }
    }
    @Test void quoteExpiryUsesExecutionTimeAfterWorkerQueueDelay() {
        var queued = new ArrayDeque<Runnable>();
        var clock = new AtomicLong(2_000);
        try (var runtime = new MarketSettlementRuntime(queued::add,Runnable::run,() -> false,clock::get)) {
            var journal = new Journal(); var assets = new Assets(); var asset = asset();
            var future = runtime.settleSale(journal,assets,UnavailableEconomyPort.INSTANCE,
                    PLAYER,asset,quote(asset),UUID.randomUUID());
            clock.set(32_000); queued.remove().run();
            assertEquals(MarketSettlementResult.Status.REJECTED,future.join().status());
            assertEquals(0,journal.begins); assertEquals(0,assets.removes);
        }
    }
}
