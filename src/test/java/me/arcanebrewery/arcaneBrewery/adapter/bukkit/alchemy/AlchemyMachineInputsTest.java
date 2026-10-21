package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.aging.*;
import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.IngredientLot;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyMachineInputsTest {
    private static final String NAMESPACE = "arcanebrewery";
    private FermentationBatchState source() {
        return new FermentationBatchState(1, new UUID(0, 22), new DistilleryProcessDraft(Map.of("minecraft:wheat", 1.0),
                Map.of("minecraft:wheat", 90.0), "distillation", new DistillationControls(78,10,80,10,1)),
                FermentationSettings.DEFAULT, 0, FermentationBatchState.Status.COMPLETED);
    }
    private ItemStack[] inputs() {
        var base = TestItems.water();
        base.edit(meta -> new FermentationBatchMetadata(NAMESPACE).write(meta, source()));
        return new ItemStack[]{base, TestItems.stack(Material.BLAZE_POWDER, 3), TestItems.stack(Material.AMETHYST_SHARD, 2)};
    }
    @Test void completionConsumesExactlyOneFromEachCatalystAndNeverMutatesInputs() {
        var inputs = inputs();
        var operation = AlchemyMachineInputs.start(inputs, NAMESPACE, 1000, 8000);
        var output = AlchemyMachineInputs.complete(inputs, NAMESPACE, operation, 9000);
        assertEquals(2, output[1].getAmount()); assertEquals(1, output[2].getAmount());
        assertEquals(3, inputs[1].getAmount()); assertEquals(2, inputs[2].getAmount());
        var preparation = new AlchemyPreparationMetadata(NAMESPACE).read(output[0].getItemMeta()).orElseThrow();
        assertEquals(operation.preparation(), preparation);
        assertEquals(2, preparation.additives().size());
        assertThrows(IllegalArgumentException.class, () -> AlchemyMachineInputs.start(output, NAMESPACE, 9001, 8000));
        assertThrows(IllegalStateException.class, () -> AlchemyMachineInputs.complete(output, NAMESPACE, operation.complete(9000), 9000));
        AlchemyMachineInputs.validateCompleted(output, NAMESPACE, operation.complete(9000));
    }
    @Test void duplicateCatalystsIncreaseDoseAndSingleCatalystIsConsumedOnce() {
        var inputs = inputs(); inputs[2] = TestItems.stack(Material.BLAZE_POWDER, 1);
        var op = AlchemyMachineInputs.start(inputs, NAMESPACE, 0, 8000);
        assertEquals(2.0, op.preparation().additives().get("arcanebrewery:fire_catalyst"));
        var output = AlchemyMachineInputs.complete(inputs, NAMESPACE, op, 8000);
        assertNull(output[2]); assertEquals(2, output[1].getAmount());
        var single = inputs(); single[2] = null;
        var singleOp = AlchemyMachineInputs.start(single, NAMESPACE, 0, 8000);
        assertEquals(1.0, singleOp.preparation().additives().get("arcanebrewery:fire_catalyst"));
    }
    @Test void mutatedLockedInputsCannotCompleteAndStackedBasesAreRejected() {
        var original = inputs();
        var op = AlchemyMachineInputs.start(original, NAMESPACE, 0, 8000);
        original[1] = TestItems.stack(Material.CHORUS_FRUIT, 1);
        assertThrows(IllegalArgumentException.class, () -> AlchemyMachineInputs.complete(original, NAMESPACE, op, 8000));
        var stacked = inputs(); stacked[0].setAmount(2);
        assertThrows(IllegalArgumentException.class, () -> AlchemyMachineInputs.start(stacked, NAMESPACE, 0, 8000));
        assertThrows(IllegalStateException.class, () -> AlchemyMachineInputs.complete(inputs(), NAMESPACE, op, 7999));
    }
    @Test void preparedAdditivesSurviveIntoFinalProcessAndChangeIdentityDeterministically() {
        var inputs = inputs();
        var op = AlchemyMachineInputs.start(inputs, NAMESPACE, 0, 8000);
        var output = AlchemyMachineInputs.complete(inputs, NAMESPACE, op, 8000);
        var additives = new AlchemyPreparationMetadata(NAMESPACE).read(output[0].getItemMeta()).orElseThrow().additives();
        var aging = new AgingBatchState(1, new UUID(0, 3), new AgingSettings("oak", 12, 18, 95), 1000);
        var lots = Map.of("minecraft:wheat", new IngredientLot(new IngredientProfile("minecraft:wheat",40,80,10,8,25,0,5),90));
        var service = new AgingService();
        var plain = service.finish(source().fermentedDraft(), aging, 121000, lots, AgingBalance.wood("oak"),0,AgingBalance.targets(),42);
        var prepared = service.finish(source().fermentedDraft(), aging,121000,lots,AgingBalance.wood("oak"),0,AgingBalance.targets(),42,additives);
        var varied = service.finish(source().fermentedDraft(), aging,121000,lots,AgingBalance.wood("oak"),0,AgingBalance.targets(),43,additives);
        assertEquals(additives, prepared.process().alchemicalAdditives());
        assertNotEquals(plain.simulation().fingerprint(), prepared.simulation().fingerprint());
        assertEquals(prepared.simulation().fingerprint(), varied.simulation().fingerprint());
    }
    @Test void legacyV1PreparationRemainsReadableButOrphanOrMalformedMetadataFailsClosed() {
        var item = TestItems.water();
        item.edit(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(NAMESPACE,"alchemy_prep_format"), PersistentDataType.INTEGER,1);
            pdc.set(new NamespacedKey(NAMESPACE,"alchemy_additive_id"), PersistentDataType.STRING,"arcanebrewery:fire_catalyst");
            pdc.set(new NamespacedKey(NAMESPACE,"alchemy_additive_dose"), PersistentDataType.DOUBLE,1.0);
        });
        var metadata = new AlchemyPreparationMetadata(NAMESPACE);
        assertEquals(Map.of("arcanebrewery:fire_catalyst",1.0), metadata.read(item.getItemMeta()).orElseThrow().additives());
        item.edit(meta -> meta.getPersistentDataContainer().remove(new NamespacedKey(NAMESPACE,"alchemy_prep_format")));
        assertThrows(IllegalArgumentException.class, () -> metadata.read(item.getItemMeta()));
        var malformed = inputs();
        ((TestItems.Stack) malformed[0]).edit(meta -> {
            meta.getPersistentDataContainer().set(new NamespacedKey(NAMESPACE,"alchemy_prep_format"),PersistentDataType.INTEGER,2);
            meta.getPersistentDataContainer().set(new NamespacedKey(NAMESPACE,"alchemy_preparation"),PersistentDataType.STRING,"%%%");
        });
        assertThrows(IllegalArgumentException.class, () -> AlchemyMachineInputs.start(malformed,NAMESPACE,0,8000));
    }
}
