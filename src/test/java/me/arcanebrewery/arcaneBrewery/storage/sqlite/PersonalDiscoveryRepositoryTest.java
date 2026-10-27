package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.storage.jdbc.PersonalDiscoveryRepository;
import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static me.arcanebrewery.arcaneBrewery.gameplay.GameplayV2Test.*;

class PersonalDiscoveryRepositoryTest {
    @TempDir Path directory;
    @Test void playersIndependentlyDiscoverAndNameWithoutTransferringServerFirst() {
        try(var provider=new SqliteStorageProvider(directory.resolve("personal.db"))) {
            var repo=(PersonalDiscoveryRepository)provider.gameplayDiscoveries();var a=UUID.randomUUID();var b=UUID.randomUUID();var p=perfect();var id=FormulationIdentity.from(p);
            assertEquals(RegistrationResult.Status.REGISTERED,repo.recordProduction(a,p,Instant.EPOCH,80,"a").status());
            var second=repo.recordProduction(b,p,Instant.EPOCH.plusSeconds(1),90,"b");
            assertEquals(RegistrationResult.Status.REGISTERED,second.status());assertEquals(a,second.originalDiscoverer());
            var mutations=new DiscoveryMutationRuntime(repo,Runnable::run);
            mutations.rename(a,id,"Emberveil").join();assertThrows(CompletionException.class,()->mutations.rename(a,id,"Emberveil II").join());mutations.rename(b,id,"Quiet Sky").join();
            assertEquals("Emberveil",repo.personalName(a,id).orElseThrow());assertEquals("Quiet Sky",repo.personalName(b,id).orElseThrow());
            assertEquals(DiscoveryVisibility.PRIVATE,repo.findForPlayer(id,a).orElseThrow().discovery().visibility());
            assertEquals(RegistrationResult.Status.ALREADY_DISCOVERED,repo.recordProduction(a,p,Instant.now(),100,"c").status());
            assertTrue(repo.recordInfo(id,a).contains("100%"));assertEquals(1,repo.personalCatalog(a,200,0).size());
        }
    }
    @Test void learnedHistorySurvivesRevocationAndDoesNotBecomeIndependent() {
        try(var provider=new SqliteStorageProvider(directory.resolve("learned.db"))) {
            var repo=(PersonalDiscoveryRepository)provider.gameplayDiscoveries();var a=UUID.randomUUID();var b=UUID.randomUUID();var id=FormulationIdentity.from(perfect());
            repo.recordProduction(a,perfect(),Instant.EPOCH,100,"a");var m=new DiscoveryMutationRuntime(repo,Runnable::run);
            m.shareFull(a,id,b).join();assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(id,b));
            m.rename(b,id,"Learned Brew").join();assertEquals("Learned Brew",repo.personalName(b,id).orElseThrow());
            m.revoke(a,id,b).join();assertInstanceOf(RecipeKnowledge.Unknown.class,repo.knowledgeFor(id,b));
            assertTrue(repo.recordInfo(id,b).contains("LEARNED"));
            repo.recordProduction(b,perfect(),Instant.now(),98,"b");assertTrue(repo.recordInfo(id,b).contains("LEARNED"));
            assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(id,b));
        }
    }
    @Test void partialKnowledgeStaysPartialAndCannotRename() {
        try(var provider=new SqliteStorageProvider(directory.resolve("partial.db"))) {
            var repo=(PersonalDiscoveryRepository)provider.gameplayDiscoveries();var a=UUID.randomUUID();var b=UUID.randomUUID();var id=FormulationIdentity.from(perfect());
            repo.recordProduction(a,perfect(),Instant.EPOCH,100,"a");var m=new DiscoveryMutationRuntime(repo,Runnable::run);
            m.sharePartial(a,id,b,Set.of(ProcessField.AGING_WOOD)).join();
            var k=assertInstanceOf(RecipeKnowledge.Partial.class,repo.knowledgeFor(id,b));assertEquals(Set.of(ProcessField.AGING_WOOD),k.disclosure().fields().keySet());
            assertEquals(1,repo.personalCatalog(b,200,0).size());assertThrows(CompletionException.class,()->m.rename(b,id,"No access").join());
        }
    }
    @Test void atomicServerFirstHasOneWinnerAcrossConnections() throws Exception {
        try(var provider=new SqliteStorageProvider(directory.resolve("race.db"));var pool=Executors.newFixedThreadPool(4)) {
            var repo=(PersonalDiscoveryRepository)provider.gameplayDiscoveries();var start=new CountDownLatch(1);var futures=new ArrayList<Future<RegistrationResult>>();
            for(int i=0;i<4;i++) {int n=i;futures.add(pool.submit(()->{start.await();return repo.recordProduction(UUID.randomUUID(),perfect(),Instant.EPOCH,100,"batch"+n);}));}
            start.countDown();var winners=new HashSet<UUID>();for(var f:futures) {var result=f.get(20,TimeUnit.SECONDS);assertEquals(RegistrationResult.Status.REGISTERED,result.status());winners.add(result.originalDiscoverer());}
            assertEquals(1,winners.size());
        }
    }
    @Test void migratedAliasesKeepExactPermissionsAndNamesAndAreRepeatable() {
        Path file=directory.resolve("migration.db");UUID a=UUID.randomUUID(),b=UUID.randomUUID(),stranger=UUID.randomUUID();var p=perfect();var old=PlayerDiscovery.discover(a,p,Instant.EPOCH).rename(a,"Old Name").sharePartial(a,b,Set.of(ProcessField.AGING_WOOD));
        try(var provider=new SqliteStorageProvider(file)) {
            provider.discoveries().insert(old);var repo=provider.gameplayDiscoveries();var id=FormulationIdentity.from(p);
            assertEquals("Old Name",repo.personalName(a,id).orElseThrow());assertInstanceOf(RecipeKnowledge.Partial.class,repo.knowledgeFor(old.fingerprint(),b));
            assertInstanceOf(RecipeKnowledge.Unknown.class,repo.knowledgeFor(id,stranger));
            new DiscoveryMutationRuntime(repo,Runnable::run).revoke(a,id,b).join();
            assertInstanceOf(RecipeKnowledge.Unknown.class,repo.knowledgeFor(old.fingerprint(),b));
            assertEquals(old.snapshot(),provider.discoveries().findByFingerprint(old.fingerprint()).orElseThrow().discovery().snapshot());
        }
        try(var provider=new SqliteStorageProvider(file)) {assertEquals(1,provider.gameplayDiscoveries().personalCatalog(a,200,0).size());}
    }
    @Test void shortIdsSearchBeyondTwoHundredAndRejectAmbiguity() {
        try(var provider=new SqliteStorageProvider(directory.resolve("catalog.db"))) {
            var repo=(PersonalDiscoveryRepository)provider.gameplayDiscoveries();var player=UUID.randomUUID();
            for(int i=0;i<205;i++) {var p=process(22,60,"arcanebrewery:wild_yeast","oak",120,Map.of("custom:herb_"+i,1.0),Map.of());repo.recordProduction(player,p,Instant.EPOCH,100,"b"+i);}
            var ids=repo.accessibleIds(player);assertEquals(205,ids.size());var last=ids.getLast();
            assertEquals(last,new DiscoveryQueryRuntime(repo,Runnable::run).resolve(player,last.value().substring(0,12)).join());
            assertThrows(CompletionException.class,()->new DiscoveryQueryRuntime(repo,Runnable::run).resolve(player,"bad").join());
        }
    }
    @Test void upgradeMakesARecoverableSnapshotBeforeChangingTheSchema() throws Exception {
        Class.forName("org.sqlite.JDBC");var file=directory.resolve("upgrade.db");
        var factory=new SqliteConnectionFactory(file);
        new me.arcanebrewery.arcaneBrewery.storage.migration.MigrationRunner(factory,SqliteSchema.migrations().subList(0,2)).migrate();
        try(var provider=new SqliteStorageProvider(file)) {assertEquals(SqliteSchema.VERSION,provider.schemaVersion());}
        List<Path> backups;try(var files=Files.list(directory)) {backups=files.filter(p->p.getFileName().toString().endsWith(".bak")).toList();}
        assertEquals(1,backups.size());
        assertEquals(2,me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcTransactions.run(new SqliteConnectionFactory(backups.getFirst()),false,me.arcanebrewery.arcaneBrewery.storage.migration.MigrationRunner::currentVersion));
        try(var provider=new SqliteStorageProvider(file)) {assertEquals(SqliteSchema.VERSION,provider.schemaVersion());}
        try(var files=Files.list(directory)) {assertEquals(1,files.filter(p->p.toString().endsWith(".bak")).count());}
    }
    @Test void collapsingOldIdentitiesPreservesEachOwnersPrivateProcessAndHistoricalName() {
        var file=directory.resolve("collapse.db");var a=UUID.randomUUID();var b=UUID.randomUUID();var outsider=UUID.randomUUID();
        var p=perfect();var alternative=process(24,90,"arcanebrewery:wild_yeast","birch",180,p.ingredientRatios(),Map.of());
        try(var provider=new SqliteStorageProvider(file)) {
            var first=PlayerDiscovery.discover(a,p,Instant.EPOCH).rename(a,"Earliest");
            var later=PlayerDiscovery.discover(b,alternative,Instant.EPOCH.plusSeconds(1)).rename(b,"Personal Birch");
            provider.discoveries().insert(later);provider.discoveries().insert(first);
            var repo=provider.gameplayDiscoveries();var id=FormulationIdentity.from(p);
            assertEquals("Earliest",repo.personalName(a,id).orElseThrow());assertEquals("Personal Birch",repo.personalName(b,id).orElseThrow());
            assertEquals(alternative,assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(id,b)).process());
            assertInstanceOf(RecipeKnowledge.Unknown.class,repo.knowledgeFor(first.fingerprint(),b));
            assertInstanceOf(RecipeKnowledge.Unknown.class,repo.knowledgeFor(id,outsider));
            assertTrue(repo.recordInfo(id,b).contains(a.toString()));
        }
    }
    @Test void nativeBatchEvidenceStaysPrivateAndRetryCannotRewriteQualityOrProducer() throws Exception {
        Path file=directory.resolve("evidence.db");UUID a=UUID.randomUUID();byte[] evidence={1,2,3};
        try(var provider=new SqliteStorageProvider(file)) {
            var repo=(PersonalDiscoveryRepository)provider.gameplayDiscoveries();
            repo.recordProduction(a,perfect(),Instant.EPOCH,90,"same-batch",evidence);
            assertEquals(RegistrationResult.Status.ALREADY_DISCOVERED,repo.recordProduction(a,perfect(),Instant.now(),90,"same-batch",evidence).status());
            assertThrows(IllegalArgumentException.class,()->repo.recordProduction(a,perfect(),Instant.now(),100,"same-batch",evidence));
            assertThrows(IllegalArgumentException.class,()->repo.recordProduction(UUID.randomUUID(),perfect(),Instant.now(),90,"same-batch",evidence));
            try(var c=new SqliteConnectionFactory(file).open(false);var q=c.createStatement();var rs=q.executeQuery("SELECT evidence_payload,quality FROM production_batches")) {
                assertTrue(rs.next());assertArrayEquals(evidence,rs.getBytes(1));assertEquals(90,rs.getDouble(2));assertFalse(rs.next());
            }
        }
    }
    @Test void collapsedMigratedGrantsRetainDifferentAliasSelectionsAndFrozenFullSources() {
        var file=directory.resolve("grants.db");var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();var p=perfect();
        var other=process(24,90,"arcanebrewery:wild_yeast","birch",180,p.ingredientRatios(),Map.of());
        var early=PlayerDiscovery.discover(a,p,Instant.EPOCH).sharePartial(a,b,Set.of(ProcessField.AGING_WOOD));
        var late=PlayerDiscovery.discover(a,other,Instant.EPOCH.plusSeconds(1)).shareFull(a,b);
        try(var provider=new SqliteStorageProvider(file)) {
            provider.discoveries().insert(early);provider.discoveries().insert(late);var repo=(PersonalDiscoveryRepository)provider.gameplayDiscoveries();
            assertInstanceOf(RecipeKnowledge.Partial.class,repo.knowledgeFor(early.fingerprint(),b));
            assertEquals(other,assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(late.fingerprint(),b)).process());
            var id=FormulationIdentity.from(p);var m=new DiscoveryMutationRuntime(repo,Runnable::run);m.rename(a,id,"Rename only").join();
            assertEquals(other,assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(late.fingerprint(),b)).process());
            m.shareFull(a,id,b).join();m.shareFull(b,id,c).join();
            repo.recordProduction(b,other,Instant.now(),90,"new-batch");
            assertEquals(p,assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(id,c)).process());
            m.revoke(a,id,b).join();assertInstanceOf(RecipeKnowledge.Unknown.class,repo.knowledgeFor(late.fingerprint(),b));
        }
    }
}
