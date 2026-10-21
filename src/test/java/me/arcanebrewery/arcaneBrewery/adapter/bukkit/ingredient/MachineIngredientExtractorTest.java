package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.application.ingredient.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineIngredientExtractorTest {
    private final IngredientMetadataCodec metadata = new IngredientMetadataCodec("arcanebrewery");
    private final IngredientItemAdapter items = new IngredientItemAdapter(BuiltInIngredients.registry(), metadata);
    private final MachineIngredientExtractor extractor = new MachineIngredientExtractor(items);

    private TestItems.Stack wheat(int quantity, int purity) {
        var item = TestItems.stack(Material.WHEAT, quantity);
        item.edit(meta -> meta.getPersistentDataContainer().set(metadata.key("ingredient_purity"), PersistentDataType.INTEGER, purity));
        return item;
    }

    @Test
    void inventoryOrderDoesNotChangeResultAndItemQuantitiesRemainUntouched() {
        var first = wheat(3, 100);
        var second = wheat(1, 0);
        var honey = TestItems.stack(Material.HONEYCOMB, 2);
        var a = extractor.extract(new ItemStack[]{first, second, honey, null});
        var b = extractor.extract(new ItemStack[]{honey, null, second, first});
        assertEquals(a, b);
        assertEquals(4, a.ingredients().get("minecraft:wheat").quantity());
        assertEquals(75, a.ingredients().get("minecraft:wheat").purity());
        assertEquals(75, a.simulationLots().get("minecraft:wheat").purity());
        assertEquals(3, first.getAmount());
        assertEquals(0, items.requireIngredient(second).purity());
    }

    @Test
    void unsupportedItemFailsExplicitlyInsteadOfBeingSilentlyDropped() {
        assertThrows(UnsupportedIngredientException.class, () -> extractor.extract(new ItemStack[]{
                wheat(1, 50), TestItems.stack(Material.STONE, 1)}));
    }

    @Test
    void selectedSlotsExcludeDecorationsWithoutGuessingAndRejectDuplicates() {
        var inventory = TestItems.inventory(TestItems.stack(Material.GLASS_BOTTLE, 3), wheat(2, 50),
                TestItems.stack(Material.GRAY_STAINED_GLASS_PANE, 1));
        var extracted = extractor.extract(inventory, 1, 0);
        assertEquals(Map.of("minecraft:glass_bottle", 3L), extracted.containers());
        assertEquals(Map.of("minecraft:wheat", 1.0), extracted.ratios());
        assertEquals(extracted, extractor.extract(inventory, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(inventory, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(inventory, 3));
        assertThrows(UnsupportedIngredientException.class, () -> extractor.extract(inventory, 0, 1, 2));
    }
}
