package me.arcanebrewery.arcaneBrewery.storage.sqlite;
import me.arcanebrewery.arcaneBrewery.storage.jdbc.PersonalDiscoveryRepository;
import me.arcanebrewery.arcaneBrewery.domain.discovery.RecipeKnowledge;
import me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity;
import me.arcanebrewery.arcaneBrewery.domain.process.BrewingCulture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static me.arcanebrewery.arcaneBrewery.gameplay.GameplayV2Test.*;
import static org.junit.jupiter.api.Assertions.*;

class BestProcessRegressionTest {
    @TempDir Path directory;
    @Test void v5UpgradeAddsBestProcessIndexWithoutChangingSavedRecipes() throws Exception {
        var file=directory.resolve("upgrade.db"); var player=UUID.randomUUID();var process=perfect();var id=FormulationIdentity.from(process);
        try(var storage=new SqliteStorageProvider(file)) { storage.gameplayDiscoveries().recordProduction(player,process,Instant.EPOCH,100,"best"); }
        var factory=new SqliteConnectionFactory(file);
        me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcTransactions.run(factory,true,c->{
            try(var q=c.createStatement()) { q.execute("DROP INDEX production_best_process_idx");q.execute("DELETE FROM schema_migrations WHERE version=6"); }
            return null;
        });
        try(var upgraded=new SqliteStorageProvider(file)) {
            assertEquals(6,upgraded.schemaVersion());
            assertEquals(process,assertInstanceOf(RecipeKnowledge.Full.class,upgraded.gameplayDiscoveries().knowledgeFor(id,player)).process());
        }
        assertTrue(me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcTransactions.<Boolean>run(factory,false,c->{
            try(var q=c.createStatement();var rows=q.executeQuery("SELECT 1 FROM sqlite_master WHERE type='index' AND name='production_best_process_idx'")) { return rows.next(); }
        }));
        try(var files=java.nio.file.Files.list(directory)) { assertEquals(1,files.filter(p->p.toString().endsWith(".bak")).count()); }
    }
    @Test void knowledgeShowsTheActualBestProcess() {
        var first=process(30,60,BrewingCulture.WILD.id(),"oak",120,Map.of("minecraft:wheat",1.0),Map.of());
        var improved=perfect();var id=FormulationIdentity.from(first);var player=UUID.randomUUID();
        assertEquals(id,FormulationIdentity.from(improved));
        try(var storage=new SqliteStorageProvider(directory.resolve("audit.db"))) {
            var repo=(PersonalDiscoveryRepository)storage.gameplayDiscoveries();
            repo.recordProduction(player,first,Instant.EPOCH,87.2,"first");
            repo.recordProduction(player,improved,Instant.EPOCH.plusSeconds(1),100,"improved");
            assertTrue(repo.recordInfo(id,player).contains("personal best 100%"));
            var shown=assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(id,player));
            assertEquals(improved,shown.process());
            assertNotEquals(first,shown.process());
            repo.recordProduction(player,first,Instant.EPOCH.plusSeconds(2),85,"later-worse");
            assertEquals(improved,assertInstanceOf(RecipeKnowledge.Full.class,repo.knowledgeFor(id,player)).process());
        }
    }
}
