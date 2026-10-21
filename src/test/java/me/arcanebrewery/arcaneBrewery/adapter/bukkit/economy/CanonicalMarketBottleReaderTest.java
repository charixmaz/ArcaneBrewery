package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.aging.AgingResult;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalMarketBottleReaderTest {
    private static final String NAMESPACE = "arcanebrewery";
    private final CanonicalMarketBottleReader reader = new CanonicalMarketBottleReader(NAMESPACE);

    @Test
    void completeV4BottleProducesBoundMarketAsset() {
        var item = canonicalBottle();

        var asset = reader.read(item).orElseThrow();

        assertEquals(64, asset.fingerprint().length());
        assertEquals(64, asset.token().length());
        assertTrue(asset.value().units() > 0);
    }

    @Test
    void stackedOrNonPotionItemsAreRejected() {
        var stacked = canonicalBottle();
        stacked.setAmount(2);
        assertTrue(reader.read(stacked).isEmpty());
        assertTrue(reader.read(TestItems.stack(Material.DIRT, 1)).isEmpty());
    }

    @Test
    void legacyOrIncompleteCanonicalMetadataIsRejected() {
        var legacy = TestItems.water();
        legacy.edit(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(NAMESPACE, "drink_format"), PersistentDataType.INTEGER, 3);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_stage"), PersistentDataType.STRING, "FINISHED");
            pdc.set(new NamespacedKey(NAMESPACE, "drink_fingerprint"), PersistentDataType.STRING, "a".repeat(64));
        });
        assertTrue(reader.read(legacy).isEmpty());

        var incomplete = TestItems.water();
        incomplete.edit(meta -> {
            var pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(NAMESPACE, "drink_format"), PersistentDataType.INTEGER, 4);
            pdc.set(new NamespacedKey(NAMESPACE, "drink_stage"), PersistentDataType.STRING, "FINISHED");
            pdc.set(new NamespacedKey(NAMESPACE, "drink_fingerprint"), PersistentDataType.STRING, "a".repeat(64));
        });
        assertTrue(reader.read(incomplete).isEmpty());
    }

    @Test
    void malformedFingerprintIsRejectedEvenWithOtherwiseValidBottle() {
        var item = canonicalBottle();
        item.edit(meta -> meta.getPersistentDataContainer().set(
                new NamespacedKey(NAMESPACE, "drink_fingerprint"),
                PersistentDataType.STRING,
                "not-a-fingerprint"
        ));
        assertTrue(reader.read(item).isEmpty());
    }

    private static TestItems.Stack canonicalBottle() {
        var process = new ProcessProfile(
                Map.of("minecraft:wheat", 1.0),
                "distillation",
                new FermentationProfile("arcanebrewery:wild_yeast", 22, 600, 90, 10),
                new DistillationProfile(10, 80, 10, 78, 2),
                new AgingProfile("oak", 172_800, 10, 18, 95),
                Map.of("arcanebrewery:fire_catalyst", 1.0)
        );
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
        var item = TestItems.water();
        item.edit(meta -> new FinalDrinkMetadata(NAMESPACE).write(meta, result, alchemy, value));
        return item;
    }
}
