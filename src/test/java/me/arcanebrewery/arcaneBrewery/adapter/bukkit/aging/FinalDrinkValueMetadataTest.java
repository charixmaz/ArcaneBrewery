package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.aging.AgingResult;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FinalDrinkValueMetadataTest {
    private static final String NAMESPACE = "arcanebrewery";

    @Test
    void v4BottleRoundTripsStoredValueAndExistingReaders() {
        var process = process();
        var quality = new BatchQuality(85, 80, 75, 82, 5, 70);
        var result = new AgingResult(
                process,
                new BatchSimulationResult(
                        DrinkFingerprint.from(process),
                        quality,
                        new Contamination(5, 0)
                )
        );
        var alchemy = new AlchemyResult(
                AlchemyEffect.FIRE_RESISTANCE, 90, 72, 20, false);
        var value = new DrinkValueCalculator().calculate(process, quality, alchemy);
        var metadata = new FinalDrinkMetadata(NAMESPACE);
        var item = TestItems.water();

        item.edit(meta -> metadata.write(meta, result, alchemy, value));

        assertEquals(value, metadata.readValue(item.getItemMeta()).orElseThrow());
        assertTrue(metadata.readConsumption(item.getItemMeta()).isPresent());
        assertEquals(alchemy, metadata.readAlchemy(item.getItemMeta()).orElseThrow());
        // Historical bottle fields remain readable, but no longer advertise a cash value.
        item.edit(meta -> me.arcanebrewery.arcaneBrewery.adapter.bukkit.ElixirPresentation.refresh(meta,metadata));
        assertEquals(value, metadata.readValue(item.getItemMeta()).orElseThrow());
        assertTrue(metadata.readConsumption(item.getItemMeta()).isPresent());
        assertFalse(item.getItemMeta().getLore().toString().contains("Value"));
    }

    @Test
    void preV4BottleDoesNotInventStoredValue() {
        var item = TestItems.water();
        item.edit(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(NAMESPACE, "drink_format"), PersistentDataType.INTEGER, 3);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_stage"), PersistentDataType.STRING, "FINISHED");
        });

        assertTrue(new FinalDrinkMetadata(NAMESPACE).readValue(item.getItemMeta()).isEmpty());
    }

    @Test
    void incompleteOrMalformedV4ValueFailsClosed() {
        var item = TestItems.water();
        item.edit(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(NAMESPACE, "drink_format"), PersistentDataType.INTEGER, 4);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_value_version"), PersistentDataType.INTEGER, 1);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_value_units"), PersistentDataType.DOUBLE, -1.0);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_value_quality"), PersistentDataType.DOUBLE, 1.0);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_value_process"), PersistentDataType.DOUBLE, 1.0);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_value_alchemy"), PersistentDataType.DOUBLE, 1.0);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_value_contamination_penalty"), PersistentDataType.DOUBLE, 1.0);
        });

        assertTrue(new FinalDrinkMetadata(NAMESPACE).readValue(item.getItemMeta()).isEmpty());
    }

    private static ProcessProfile process() {
        return new ProcessProfile(
                Map.of("minecraft:wheat", 1.0),
                "distillation",
                new FermentationProfile("arcanebrewery:wild_yeast", 22, 600, 90, 10),
                new DistillationProfile(10, 80, 10, 78, 2),
                new AgingProfile("oak", 172_800, 10, 18, 95),
                Map.of("arcanebrewery:fire_catalyst", 1.0)
        );
    }
}
