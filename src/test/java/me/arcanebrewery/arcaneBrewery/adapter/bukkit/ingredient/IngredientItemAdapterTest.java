package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.IngredientPurityEngine;
import me.arcanebrewery.arcaneBrewery.application.ingredient.*;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IngredientItemAdapterTest {
    private final IngredientMetadataCodec metadata = new IngredientMetadataCodec("arcanebrewery");
    private final IngredientItemAdapter items = new IngredientItemAdapter(BuiltInIngredients.registry(), metadata);

    private TestItems.Stack legacy(Material type, int amount, int purity) {
        var item = TestItems.stack(type, amount);
        item.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("ingredient_purity"), PersistentDataType.INTEGER, purity));
        return item;
    }

    @Test
    void everyBuiltInMinecraftMaterialMapsToItsExpectedStableId() {
        for (var entry : items.registry().externalMappings().entrySet()) {
            Material material = Material.valueOf(entry.getKey().substring("minecraft:".length()).toUpperCase(Locale.ROOT));
            assertEquals(entry.getValue(), items.requireIngredient(TestItems.stack(material, 1)).ingredientId());
        }
    }

    @Test
    void unsupportedOrRenamedItemsNeverBecomeCustomIngredients() {
        var stone = TestItems.stack(Material.STONE, 1);
        stone.edit(meta -> { meta.setDisplayName("§b§lExclusive Brewer's Grain"); meta.setLore(List.of("100% pure")); });
        assertThrows(UnsupportedIngredientException.class, () -> items.requireIngredient(stone));
        var wheat = TestItems.stack(Material.WHEAT, 1);
        wheat.edit(meta -> meta.setDisplayName("Premium Moon Grain"));
        assertEquals("minecraft:wheat", items.requireIngredient(wheat).ingredientId());
    }

    @Test
    void legacyPurityReadsWithoutMutatingOrInventingIdentityMetadata() {
        var item = legacy(Material.APPLE, 3, 75);
        assertEquals(75, items.requireIngredient(item).purity());
        assertEquals(3, items.requireIngredient(item).quantity());
        assertFalse(item.getItemMeta().getPersistentDataContainer().has(metadata.key("ingredient_format")));
    }

    @Test
    void canonicalMetadataPurityModifiersAndProvenanceRoundTrip() {
        var provenance = new IngredientProvenance("arcanebrewery:wheat_harvest", Optional.of(new UUID(0, 123)));
        var ingredient = new IngredientInstance(BuiltInIngredients.PREMIUM_GRAIN, 75, 3,
                new IngredientModifiers(2.5, 3.5), Optional.of(provenance));
        var item = items.write(TestItems.stack(Material.WHEAT, 3), ingredient, false);
        assertEquals(ingredient, items.requireIngredient(item));
        var data = item.getItemMeta().getPersistentDataContainer();
        assertEquals(1, data.get(metadata.key("ingredient_format"), PersistentDataType.INTEGER));
        assertEquals(75, data.get(metadata.key("ingredient_purity"), PersistentDataType.INTEGER));
        assertFalse(data.has(metadata.key("premium_grain_tag")));
    }

    @Test
    void oldPremiumGrainResolvesSeparatelyAndMigratesOnWash() {
        var item = legacy(Material.WHEAT, 4, 50);
        item.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("premium_grain_tag"), PersistentDataType.STRING, "TRUE"));
        assertEquals(BuiltInIngredients.PREMIUM_GRAIN, items.requireIngredient(item).ingredientId());
        assertTrue(metadata.isPremium(item));
        assertNotEquals(items.requireIngredient(TestItems.stack(Material.WHEAT, 1)).ingredientId(),
                items.requireIngredient(item).ingredientId());
        var washed = IngredientPurityEngine.processWashCycle(items, item);
        assertEquals(BuiltInIngredients.PREMIUM_GRAIN, items.requireIngredient(washed).ingredientId());
        assertEquals(75, items.requireIngredient(washed).purity());
        assertTrue(metadata.isPremium(washed));
        assertFalse(washed.getItemMeta().getPersistentDataContainer().has(metadata.key("premium_grain_tag")));
        assertEquals(4, item.getAmount());
        assertEquals(1, washed.getAmount());
    }

    @Test
    void canonicalIdentityWinsOverStalePremiumFlagAndFlagsDoNotMakeOtherMaterialsPremium() {
        var item = items.write(TestItems.stack(Material.WHEAT, 1),
                new IngredientInstance("minecraft:wheat", 25, 1, IngredientModifiers.NONE, Optional.empty()), false);
        var clone = (TestItems.Stack) item;
        clone.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("premium_grain_tag"), PersistentDataType.STRING, "TRUE"));
        assertEquals("minecraft:wheat", items.requireIngredient(item).ingredientId());
        assertFalse(metadata.isPremium(item));
        var apple = legacy(Material.APPLE, 1, 50);
        apple.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("premium_grain_tag"), PersistentDataType.STRING, "TRUE"));
        assertEquals("minecraft:apple", items.requireIngredient(apple).ingredientId());
    }

    @Test
    void washingAdds25CapsAt100AndDoesNotMutateOriginalOrLoseMetadata() {
        var foreignKey = new NamespacedKey("anotherplugin", "owner");
        var item = legacy(Material.WHEAT, 8, 0);
        item.edit(meta -> {
            meta.getPersistentDataContainer().set(foreignKey, PersistentDataType.STRING, "kept");
            meta.setCustomModelData(1234); meta.setUnbreakable(true); meta.setLore(List.of("Foreign lore"));
        });
        org.bukkit.inventory.ItemStack washed = item;
        for (int expected : new int[]{25, 50, 75, 100, 100}) {
            washed = IngredientPurityEngine.processWashCycle(items, washed);
            assertEquals(expected, items.requireIngredient(washed).purity());
            assertEquals(expected, IngredientPurityEngine.getPurity(items, washed));
            assertEquals(1, washed.getAmount());
            assertEquals("kept", washed.getItemMeta().getPersistentDataContainer().get(foreignKey, PersistentDataType.STRING));
            assertEquals(1234, washed.getItemMeta().getCustomModelData());
            assertTrue(washed.getItemMeta().isUnbreakable());
            var lore = washed.getItemMeta().getLore();
            assertTrue(lore.contains("Foreign lore"));
            assertEquals(1, lore.stream().filter(line -> line.contains("Purity Level:")).count());
            assertFalse(lore.stream().anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("toxicity")
                    || line.toLowerCase(Locale.ROOT).contains("affinity")));
        }
        assertEquals(8, item.getAmount());
        assertEquals(0, items.requireIngredient(item).purity());
        assertEquals(List.of("Foreign lore"), item.getItemMeta().getLore());
    }

    @Test
    void legacyWashPresentationMigratesWithoutStalePurityLore() {
        var item = legacy(Material.WHEAT, 1, 75);
        item.edit(meta -> meta.setLore(List.of("External line", "§7Status: §ePartially Washed",
                "§7Purity Level: §e75%", "", "§8§oRequires more washing cycles.")));
        var washed = IngredientPurityEngine.processWashCycle(items, item);
        assertTrue(washed.getItemMeta().getLore().contains("External line"));
        assertFalse(washed.getItemMeta().getLore().stream().anyMatch(line -> line.contains("75%")));
        assertTrue(washed.getItemMeta().getLore().stream().anyMatch(line -> line.contains("100%")));
    }

    @Test
    void oldWashedMarkerIsPreservedButNeverInventsNumericPurity() {
        var item = TestItems.stack(Material.WHEAT, 1);
        item.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("washed_ingredient"), PersistentDataType.STRING, "TRUE"));
        assertTrue(metadata.legacyWashed(item));
        assertEquals(0, items.requireIngredient(item).purity());
        var washed = IngredientPurityEngine.processWashCycle(items, item);
        assertEquals(25, items.requireIngredient(washed).purity());
        assertTrue(metadata.legacyWashed(washed));
        assertFalse(metadata.legacyWashed(IngredientPurityEngine.processWashCycle(items, TestItems.stack(Material.WHEAT, 1))));
    }

    @Test
    void invalidOrUnknownCanonicalMetadataFailsInsteadOfFallingBackToVanilla() {
        var future = legacy(Material.WHEAT, 1, 25);
        future.edit(meta -> {
            meta.getPersistentDataContainer().set(metadata.key("ingredient_format"), PersistentDataType.INTEGER, 2);
            meta.getPersistentDataContainer().set(metadata.key("ingredient_id"), PersistentDataType.STRING, "minecraft:wheat");
        });
        assertThrows(IllegalArgumentException.class, () -> items.requireIngredient(future));
        var partial = legacy(Material.WHEAT, 1, 25);
        partial.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("ingredient_id"), PersistentDataType.STRING, "minecraft:wheat"));
        assertThrows(IllegalArgumentException.class, () -> items.requireIngredient(partial));
        var unknown = legacy(Material.WHEAT, 1, 25);
        unknown.edit(meta -> {
            meta.getPersistentDataContainer().set(metadata.key("ingredient_format"), PersistentDataType.INTEGER, 1);
            meta.getPersistentDataContainer().set(metadata.key("ingredient_id"), PersistentDataType.STRING, "server:unknown");
        });
        assertThrows(UnsupportedIngredientException.class, () -> items.requireIngredient(unknown));
    }

    @Test
    void invalidMetadataTypesAndModifierValuesAreRejected() {
        var wrong = TestItems.stack(Material.WHEAT, 1);
        wrong.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("ingredient_purity"), PersistentDataType.STRING, "100"));
        assertThrows(IllegalArgumentException.class, () -> items.requireIngredient(wrong));
        var canonical = (TestItems.Stack) items.write(TestItems.stack(Material.WHEAT, 1),
                new IngredientInstance("minecraft:wheat", 50, 1, IngredientModifiers.NONE, Optional.empty()), false);
        canonical.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("ingredient_flavor_bonus"), PersistentDataType.DOUBLE, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> items.requireIngredient(canonical));
    }

    @Test
    void registeredCustomIngredientCanUseExplicitPdcOnAnOtherwiseUnsupportedCarrier() {
        var definition = new IngredientDefinition(new IngredientProfile("myitems:moon_grain", 50, 50, 0, 0, 50, 0, 20),
                "Moon Grain", false, IngredientModifiers.NONE);
        var customItems = new IngredientItemAdapter(new IngredientRegistry(List.of(definition), Map.of()), metadata);
        var instance = new IngredientInstance(definition.id(), 50, 2, IngredientModifiers.NONE, Optional.empty());
        var item = customItems.write(TestItems.stack(Material.PAPER, 2), instance, false);
        assertEquals(instance, customItems.requireIngredient(item));
        assertThrows(UnsupportedIngredientException.class, () -> items.requireIngredient(item));
    }

    @Test
    void quantityMismatchIsRejectedWithoutChangingInput() {
        var item = TestItems.stack(Material.WHEAT, 4);
        assertThrows(IllegalArgumentException.class, () -> items.write(item,
                new IngredientInstance("minecraft:wheat", 50, 1, IngredientModifiers.NONE, Optional.empty()), false));
        assertEquals(4, item.getAmount());
        assertFalse(item.hasItemMeta());
    }

    @Test
    void containersLiquidsAndEmptySlotsAreClassifiedSeparately() {
        assertInstanceOf(IngredientInput.Empty.class, items.classify(null));
        assertInstanceOf(IngredientInput.Empty.class, items.classify(TestItems.stack(Material.AIR, 1)));
        assertEquals(new IngredientInput.Container("minecraft:glass_bottle", 3), items.classify(TestItems.stack(Material.GLASS_BOTTLE, 3)));
        assertEquals(new IngredientInput.Container("minecraft:bucket", 1), items.classify(TestItems.stack(Material.BUCKET, 1)));
        assertEquals(new IngredientInput.BaseLiquid("minecraft:water", 1), items.classify(TestItems.stack(Material.WATER_BUCKET, 1)));
    }

    @Test
    void waterPotionsAreBaseLiquidButLegacyWortIsNot() {
        var water = TestItems.water();
        assertEquals(new IngredientInput.BaseLiquid("minecraft:water", 1), items.classify(water));
        water.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("brew_stage"), PersistentDataType.STRING, "WORT_BEER"));
        assertThrows(UnsupportedIngredientException.class, () -> items.classify(water));
    }
}
