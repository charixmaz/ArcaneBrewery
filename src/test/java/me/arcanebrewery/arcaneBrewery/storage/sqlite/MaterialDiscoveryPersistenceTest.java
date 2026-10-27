package me.arcanebrewery.arcaneBrewery.storage.sqlite;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class MaterialDiscoveryPersistenceTest {
    @TempDir Path dir;
    private static final MaterialKey WHEAT=new MaterialKey(MaterialKey.Kind.INGREDIENT,"minecraft:wheat");
    private static final MaterialKey FIRE=new MaterialKey(MaterialKey.Kind.CATALYST,"arcanebrewery:fire_catalyst");
    @Test void duplicatePickupsAreIdempotentAcrossRestartAndDifferentPlayersAreIndependent() {
        var file=dir.resolve("progress.db");var a=UUID.randomUUID();var b=UUID.randomUUID();
        try(var provider=new SqliteStorageProvider(file)) {
            var repo=provider.materials();assertEquals(Set.of(WHEAT,FIRE),repo.obtain(a,Set.of(WHEAT,FIRE),Instant.EPOCH).newlyUnlocked());
            assertTrue(repo.obtain(a,Set.of(WHEAT),Instant.now()).newlyUnlocked().isEmpty());
            assertEquals(Set.of(WHEAT),repo.obtain(b,Set.of(WHEAT),Instant.now()).newlyUnlocked());
            assertTrue(provider.gameplayDiscoveries().personalCatalog(a,200,0).isEmpty());
        }
        try(var provider=new SqliteStorageProvider(file)) {assertEquals(Set.of(WHEAT,FIRE),provider.materials().obtain(a,Set.of(),Instant.now()).known());assertTrue(provider.materials().obtain(a,Set.of(FIRE),Instant.now()).newlyUnlocked().isEmpty());}
    }
    @Test void runtimeSnapshotsPossessionAndDoesNoDatabaseWorkOnTheCallerThread() {
        var queued=new ArrayList<Runnable>();var player=UUID.randomUUID();var input=new HashSet<>(Set.of(WHEAT));
        try(var provider=new SqliteStorageProvider(dir.resolve("async.db"))) {
            var runtime=new MaterialDiscoveryRuntime(provider.materials(),queued::add);
            var result=runtime.obtain(player,input);input.clear();assertFalse(result.isDone());
            assertTrue(provider.materials().obtain(player,Set.of(),Instant.EPOCH).known().isEmpty());
            queued.removeFirst().run();assertEquals(Set.of(WHEAT),result.join().newlyUnlocked());
        }
    }
    @Test void concurrentObtainsHaveOneNotificationWinner() throws Exception {
        try(var provider=new SqliteStorageProvider(dir.resolve("race.db"));var pool=Executors.newFixedThreadPool(2)) {
            var player=UUID.randomUUID();var start=new CountDownLatch(1);
            Callable<Integer> task=()->{start.await();return provider.materials().obtain(player,Set.of(FIRE),Instant.EPOCH).newlyUnlocked().size();};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertEquals(1,a.get()+b.get());
        }
    }
    @Test void v3UpgradeBacksUpExistingRecipesWithoutChangingThem() throws Exception {
        Class.forName("org.sqlite.JDBC");var file=dir.resolve("v3.db");var factory=new SqliteConnectionFactory(file);
        new me.arcanebrewery.arcaneBrewery.storage.migration.MigrationRunner(factory,SqliteSchema.migrations().subList(0,3)).migrate();
        try(var provider=new SqliteStorageProvider(file)){assertEquals(SqliteSchema.VERSION,provider.schemaVersion());assertNotNull(provider.materials());}
        List<Path> backups;try(var files=Files.list(dir)){backups=files.filter(p->p.toString().endsWith(".bak")).toList();}assertEquals(1,backups.size());
        assertEquals(3,me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcTransactions.run(new SqliteConnectionFactory(backups.getFirst()),false,me.arcanebrewery.arcaneBrewery.storage.migration.MigrationRunner::currentVersion));
    }
    @Test void catalogAdditionsAndSeparateKindsDoNotEraseEarlierProgress() {
        try(var provider=new SqliteStorageProvider(dir.resolve("growth.db"))) {
            var player=UUID.randomUUID();var sameIdOtherKind=new MaterialKey(MaterialKey.Kind.CATALYST,WHEAT.registryId());
            provider.materials().obtain(player,Set.of(WHEAT),Instant.EPOCH);
            var future=new MaterialKey(MaterialKey.Kind.CATALYST,"custom:future_supported_reagent");
            var update=provider.materials().obtain(player,Set.of(sameIdOtherKind,future),Instant.now());
            assertEquals(Set.of(WHEAT,sameIdOtherKind,future),update.known());assertEquals(2,update.newlyUnlocked().size());
        }
    }
}
