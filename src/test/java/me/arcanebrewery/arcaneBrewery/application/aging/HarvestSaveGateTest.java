package me.arcanebrewery.arcaneBrewery.application.aging;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class HarvestSaveGateTest {
    @Test void failedSaveRetainsBatchAndAllowsASecondAttempt() {
        var gate=new HarvestSaveGate<String>(); var save=new CompletableFuture<String>();
        var source=new AtomicInteger(1); var bottles=new AtomicInteger();
        java.util.function.BiConsumer<String,Throwable> delivery=(value,error)->{
            if(error==null) { source.decrementAndGet(); bottles.incrementAndGet(); }
        };
        assertTrue(gate.submit(0,()->save,Runnable::run,delivery));
        assertEquals(1,source.get()); assertEquals(0,bottles.get());
        assertFalse(gate.submit(0,()->CompletableFuture.completedFuture("duplicate"),Runnable::run,delivery));
        save.completeExceptionally(new IllegalStateException("database unavailable"));
        assertEquals(1,source.get()); assertEquals(0,bottles.get()); assertFalse(gate.pending(0));
        assertTrue(gate.submit(0,()->CompletableFuture.completedFuture("saved"),Runnable::run,delivery));
        assertEquals(0,source.get()); assertEquals(1,bottles.get());
    }
    @Test void closingDuringSaveOrAfterQueuedCompletionNeverDelivers() {
        for(boolean finishBeforeClose:List.of(false,true)) {
            var server=new ArrayDeque<Runnable>(); var save=new CompletableFuture<String>();
            var deliveries=new AtomicInteger(); var gate=new HarvestSaveGate<String>();
            gate.submit(2,()->save,server::add,(value,error)->deliveries.incrementAndGet());
            if(finishBeforeClose) save.complete("saved");
            gate.close(); save.complete("saved");
            while(!server.isEmpty()) server.remove().run();
            assertEquals(0,deliveries.get()); assertFalse(gate.pending(2));
            assertFalse(gate.submit(2,()->save,server::add,(value,error)->fail()));
        }
    }
    @Test void rejectedPersistenceAndServerSchedulingLeaveLaneRetryable() {
        var gate=new HarvestSaveGate<String>(); var failures=new AtomicInteger();
        gate.submit(4,()->{throw new RejectedExecutionException();},Runnable::run,
                (value,error)->{assertNotNull(error);failures.incrementAndGet();});
        assertEquals(1,failures.get()); assertFalse(gate.pending(4));
        gate.submit(4,()->CompletableFuture.completedFuture("saved"),
                task->{throw new RejectedExecutionException();},(value,error)->fail());
        assertFalse(gate.pending(4));
        assertTrue(gate.submit(4,()->CompletableFuture.completedFuture("saved"),Runnable::run,
                (value,error)->assertNull(error)));
    }
}

