package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class EscrowThreadingRegressionTest {
    @TempDir Path directory;
    private static final UUID PLAYER=UUID.randomUUID();
    private static MarketAsset asset() { return MarketAsset.from("b".repeat(64),new DrinkValue(1,100,50,30,20,10)); }
    private static MarketQuote quote(MarketAsset a) {
        return new MarketQuote(1,UUID.randomUUID(),a.fingerprint(),a.token(),MarketSide.PLAYER_SELLS,100,75.25,1_000,31_000);
    }
    static final class Escrow implements MarketEscrowStore {
        final MarketEscrowStore delegate; Runnable afterStage=()->{};
        final List<String> calls=new CopyOnWriteArrayList<>();
        Escrow(MarketEscrowStore delegate) { this.delegate=delegate; }
        void check() { assertEquals("market-worker",Thread.currentThread().getName());calls.add(Thread.currentThread().getName()); }
        public boolean stage(UUID id,UUID p,String token,int slot,byte[] bytes) { check();var result=delegate.stage(id,p,token,slot,bytes);afterStage.run();return result; }
        public void markRemoved(UUID id) { check();delegate.markRemoved(id); }
        public void markRestored(UUID id) { check();delegate.markRestored(id); }
        public void release(UUID id) { check();delegate.release(id); }
        public void markReconcile(UUID id) { check();delegate.markReconcile(id); }
        public Optional<MarketEscrowEntry> find(UUID id) { check();return delegate.find(id); }
        public List<MarketEscrowEntry> unresolved(int n) { check();return delegate.unresolved(n); }
    }
    static final class Inventory implements MarketInventoryPort {
        final MarketSettlementRuntime runtime;
        final AtomicBoolean present=new AtomicBoolean(true);
        final AtomicInteger removals=new AtomicInteger(),restores=new AtomicInteger();
        Inventory(MarketSettlementRuntime runtime) { this.runtime=runtime; }
        void check() { assertEquals("test-server",Thread.currentThread().getName()); }
        public Optional<Item> snapshot(UUID p,MarketAsset a,UUID id) {
            return runtime.inventory(()->{check();return present.get()?Optional.of(new Item(2,new byte[]{1,2})):Optional.empty();});
        }
        public boolean removeExact(UUID p,Item item) {
            return runtime.inventory(()->{check();if(!present.compareAndSet(true,false))return false;removals.incrementAndGet();return true;});
        }
        public boolean restore(UUID p,MarketAsset a,Item item) {
            return runtime.inventory(()->{check();if(!present.compareAndSet(false,true))return false;restores.incrementAndGet();return true;});
        }
    }
    @Test void saleKeepsAllEscrowCallsOffServerAndAllInventoryCallsOnServer() throws Exception { runSale(true,false); }
    @Test void rejectedPaymentRestoresTheBottleWithEscrowWritesStillOffServer() throws Exception { runSale(false,false); }
    @Test void movingBottleDuringDatabaseStageRejectsWithoutRemovingOrPaying() throws Exception { runSale(true,true); }
    private void runSale(boolean paid,boolean moved) throws Exception {
        try(var storage=new SqliteStorageProvider(directory.resolve("market.db"));
            var worker=Executors.newSingleThreadExecutor(r->new Thread(r,"market-worker"));
            var server=Executors.newSingleThreadExecutor(r->new Thread(r,"test-server"));
            var runtime=new MarketSettlementRuntime(worker,server,()->Thread.currentThread().getName().equals("test-server"),()->2_000)) {
            var escrow=new Escrow(storage.marketEscrow());var inventory=new Inventory(runtime);
            var deposits=new AtomicInteger();
            if(moved) escrow.afterStage=()->runtime.inventory(()->{inventory.check();inventory.present.set(false);return true;});
            var economy=new EconomyPort() {
                public boolean canAfford(UUID p,BigDecimal amount) { return true; }
                public boolean withdraw(UUID p,BigDecimal amount,UUID id) { return false; }
                public boolean deposit(UUID p,BigDecimal amount,UUID id) { assertEquals("market-worker",Thread.currentThread().getName());deposits.incrementAndGet();return paid; }
            };
            var id=UUID.randomUUID();var asset=asset();
            var result=runtime.settleWorkerSale(storage.marketJournal(),new EscrowMarketAssetPort(inventory,escrow),
                    economy,PLAYER,asset,quote(asset),id).get(5,TimeUnit.SECONDS);
            assertEquals(!moved&&paid?MarketSettlementResult.Status.COMMITTED:MarketSettlementResult.Status.REJECTED,result.status());
            assertEquals(moved?0:1,deposits.get());assertEquals(moved?0:1,inventory.removals.get());
            assertEquals(!moved&&!paid?1:0,inventory.restores.get());assertFalse(escrow.calls.isEmpty());
            assertTrue(storage.marketEscrow().unresolved(10).isEmpty());
            assertEquals(!moved&&paid?MarketEscrowEntry.State.RELEASED:MarketEscrowEntry.State.RESTORED,
                    storage.marketEscrow().find(id).orElseThrow().state());
        }
    }
}

