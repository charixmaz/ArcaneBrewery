package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PersonalNameCacheTest {
    private static final UUID PLAYER=UUID.randomUUID();
    private static DrinkFingerprint id(int n) { return new DrinkFingerprint(String.format("%064x",n)); }
    @Test void repeatedEventsShareOneLookupAndCacheItsResult() {
        var queue=new ArrayDeque<Runnable>(); var cache=new PersonalNameCache(()->0,8,2,100);
        var calls=new AtomicInteger();
        var a=cache.get(PLAYER,id(1),queue::add,()->{calls.incrementAndGet();return Optional.of("Name");});
        var b=cache.get(PLAYER,id(1),queue::add,()->{fail("duplicate query");return Optional.empty();});
        assertEquals(1,queue.size()); queue.remove().run();
        assertEquals("Name",a.join().orElseThrow()); assertEquals(a.join(),b.join()); assertEquals(1,calls.get());
        assertEquals(a.join(),cache.get(PLAYER,id(1),queue::add,Optional::empty).join());
        assertTrue(queue.isEmpty());
    }
    @Test void negativeNamesAreCachedButExpire() {
        var clock=new AtomicLong();var cache=new PersonalNameCache(clock::get,4,2,100);var calls=new AtomicInteger();
        java.util.function.Supplier<Optional<String>> missing=()->{calls.incrementAndGet();return Optional.empty();};
        cache.get(PLAYER,id(1),Runnable::run,missing).join();cache.get(PLAYER,id(1),Runnable::run,missing).join();
        assertEquals(1,calls.get());clock.set(100);
        cache.get(PLAYER,id(1),Runnable::run,missing).join();assertEquals(2,calls.get());
    }
    @Test void renameInvalidatesPendingResultsAndOldLookupCannotOverwriteNewName() {
        var queue=new ArrayDeque<Runnable>();var cache=new PersonalNameCache(()->0,8,2,100);
        var old=cache.get(PLAYER,id(1),queue::add,()->Optional.of("Old"));
        cache.invalidate(PLAYER); assertTrue(old.isCompletedExceptionally());
        var fresh=cache.get(PLAYER,id(1),queue::add,()->Optional.of("New"));
        queue.removeLast().run();queue.removeFirst().run();
        assertEquals("New",fresh.join().orElseThrow());
        assertEquals(fresh.join(),cache.get(PLAYER,id(1),queue::add,Optional::empty).join());assertTrue(queue.isEmpty());
    }
    @Test void outstandingQueriesAreBoundedAndCapacityCanEvictCompletedEntries() {
        var queue=new ArrayDeque<Runnable>();var cache=new PersonalNameCache(()->0,2,2,100);
        cache.get(PLAYER,id(1),queue::add,()->Optional.of("One"));
        cache.get(PLAYER,id(2),queue::add,()->Optional.of("Two"));
        assertTrue(cache.get(PLAYER,id(3),queue::add,()->Optional.of("Three")).join().isEmpty());
        assertEquals(2,queue.size());queue.remove().run();
        var third=cache.get(PLAYER,id(3),queue::add,()->Optional.of("Three"));
        assertEquals(2,queue.size());while(!queue.isEmpty())queue.remove().run();
        assertEquals("Three",third.join().orElseThrow());
    }
    @Test void failedOrRejectedLookupsCanBeRetried() {
        var cache=new PersonalNameCache(()->0,2,1,100);
        assertTrue(cache.get(PLAYER,id(1),task->{throw new RejectedExecutionException();},Optional::empty).isCompletedExceptionally());
        assertTrue(cache.get(PLAYER,id(1),Runnable::run,()->{throw new IllegalStateException();}).isCompletedExceptionally());
        assertEquals("Recovered",cache.get(PLAYER,id(1),Runnable::run,()->Optional.of("Recovered")).join().orElseThrow());
    }
}

