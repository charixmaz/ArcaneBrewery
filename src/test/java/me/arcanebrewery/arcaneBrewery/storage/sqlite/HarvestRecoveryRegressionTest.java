package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.CaskHarvestMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.aging.HarvestSaveGate;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.application.storage.RegistrationResult;
import me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity;
import me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcTransactions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static me.arcanebrewery.arcaneBrewery.gameplay.GameplayV2Test.perfect;
import static org.junit.jupiter.api.Assertions.*;

class HarvestRecoveryRegressionTest {
    @TempDir Path directory;
    @Test void failedSaveRetainsFrozenSourceAndRestartRetryRegistersIt() { recover(false); }
    @Test void savedDiscoveryWithCancelledDeliveryCanBeRetriedAfterRestartWithoutDuplicateBatch() { recover(true); }
    private void recover(boolean commitBeforeRestart) {
        var file=directory.resolve("harvest.db");var player=UUID.randomUUID();var process=perfect();
        var metadata=new CaskHarvestMetadata("arcanebrewery");var source=TestItems.water();
        var frozen=new CaskHarvestMetadata.Checkpoint(123_000,player,"Brewer");
        source.edit(meta->metadata.write(meta,frozen));var retainedSource=source.clone();
        var bottles=new AtomicInteger();var retained=new AtomicBoolean(true);var queued=new ArrayDeque<Runnable>();
        try(var storage=new SqliteStorageProvider(file)) {
            var gate=new HarvestSaveGate<RegistrationResult>();
            var runtime=new DiscoveryRuntime(new DiscoveryRegistrationService(storage.gameplayDiscoveries()),
                    commitBeforeRestart?Runnable::run:task->{throw new RejectedExecutionException("storage worker unavailable");});
            gate.submit(0,()->runtime.register(player,process,Instant.ofEpochMilli(frozen.harvestedAt()),100,"same-batch",new byte[]{1}),
                    queued::add,(result,error)->{if(error==null){retained.set(false);bottles.incrementAndGet();}});
            if(!commitBeforeRestart) queued.remove().run();
            gate.close();while(!queued.isEmpty())queued.remove().run();
            assertTrue(retained.get());assertEquals(0,bottles.get());
        }
        try(var reopened=new SqliteStorageProvider(file)) {
            var checkpoint=metadata.read(retainedSource.getItemMeta()).orElseThrow();assertEquals(frozen,checkpoint);
            var runtime=new DiscoveryRuntime(new DiscoveryRegistrationService(reopened.gameplayDiscoveries()),Runnable::run);
            var gate=new HarvestSaveGate<RegistrationResult>();
            gate.submit(0,()->runtime.register(checkpoint.producer(),process,Instant.ofEpochMilli(checkpoint.harvestedAt()),100,"same-batch",new byte[]{1}),
                    Runnable::run,(result,error)->{
                        assertNull(error);assertEquals(commitBeforeRestart?RegistrationResult.Status.ALREADY_DISCOVERED:RegistrationResult.Status.REGISTERED,result.status());
                        assertTrue(retained.compareAndSet(true,false));bottles.incrementAndGet();
                    });
            assertFalse(retained.get());assertEquals(1,bottles.get());
            assertTrue(reopened.gameplayDiscoveries().findForPlayer(FormulationIdentity.from(process),player).isPresent());
        }
        int batches=JdbcTransactions.run(new SqliteConnectionFactory(file),false,connection->{
            try(var query=connection.createStatement();var rows=query.executeQuery("SELECT COUNT(*) FROM production_batches")) { rows.next();return rows.getInt(1); }
        });
        assertEquals(1,batches);
    }
}

