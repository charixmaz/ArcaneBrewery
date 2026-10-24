package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery.DistilleryBatchMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyPreparationMetadata;
import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyPreparation;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MaturationInputPolicyTest {
    private final String ns="arcanebrewery";
    private final MaturationInputPolicy policy=new MaturationInputPolicy(ns);
    private DistilleryProcessDraft draft() {
        return new DistilleryProcessDraft(Map.of("minecraft:wheat",1.0),Map.of("minecraft:wheat",90.0),"distillation",DistillationControls.DEFAULT);
    }
    @Test void canonicalRefinedBaseCannotBypassCultivationOrEnterLegacyRngPath() {
        var meta=TestItems.water().getItemMeta();
        var state=new DistilleryBatchState(1,UUID.randomUUID(),new MachineLocation(UUID.randomUUID(),0,64,0),draft(),0,1000,DistilleryBatchState.Status.COMPLETED);
        new DistilleryBatchMetadata(ns).write(meta,state,"DISTILLED_BASE");
        meta.getPersistentDataContainer().set(new NamespacedKey(ns,"brew_stage"),PersistentDataType.STRING,"WORT_BEER");
        assertFalse(policy.legacyEligible(meta));
        assertThrows(IllegalArgumentException.class,() -> policy.completedSource(meta,100_000));
        assertEquals(state,new DistilleryBatchMetadata(ns).read(meta).orElseThrow());
    }
    @Test void legitimateLegacyWortRemainsCompatibleWithoutInventingCanonicalData() {
        var meta=TestItems.water().getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(ns,"brew_stage"),PersistentDataType.STRING,"WORT_BEER");
        var keys=meta.getPersistentDataContainer().getKeys();
        assertTrue(policy.legacyEligible(meta)); assertTrue(policy.completedSource(meta,100_000).isEmpty());
        assertEquals(keys,meta.getPersistentDataContainer().getKeys());
    }
    @Test void readyCultivationCompletesLazilyWithCapturedTimingAndOriginalBatchId() {
        var meta=TestItems.water().getItemMeta();
        var original=new FermentationBatchState(1,UUID.randomUUID(),draft(),FermentationSettings.DEFAULT,1000,FermentationBatchState.Status.FERMENTING);
        new FermentationBatchMetadata(ns).write(meta,original);
        assertThrows(IllegalArgumentException.class,() -> policy.completedSource(meta,60_000));
        assertEquals(original,new FermentationBatchMetadata(ns).read(meta).orElseThrow());
        var completed=policy.completedSource(meta,61_000).orElseThrow();
        assertEquals(original.batchId(),completed.batchId()); assertEquals(original.settings(),completed.settings());
        assertEquals(FermentationBatchState.Status.COMPLETED,completed.status()); assertFalse(policy.legacyEligible(meta));
    }
    @Test void orphanAndMalformedCanonicalMetadataNeverBecomesLegacy() {
        var meta=TestItems.water().getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(ns,"fermentation_format"),PersistentDataType.INTEGER,1);
        assertFalse(policy.legacyEligible(meta)); assertThrows(IllegalArgumentException.class,() -> policy.completedSource(meta,100_000));
        var prepared=TestItems.water().getItemMeta();
        new AlchemyPreparationMetadata(ns).write(prepared,new AlchemyPreparation(Map.of("custom:fire",1.0)));
        assertFalse(policy.legacyEligible(prepared)); assertThrows(IllegalArgumentException.class,() -> policy.completedSource(prepared,100_000));
    }
}
