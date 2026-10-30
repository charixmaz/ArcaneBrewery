package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Compatibility facade: item purity is interpreted only by the ingredient metadata adapter. */
public final class IngredientPurityEngine {
    private IngredientPurityEngine() {}

    public static ItemStack processWashCycle(IngredientItemAdapter items, ItemStack rawItem) {
        ItemStack single = rawItem.clone();
        single.setAmount(1);
        var ingredient = items.requireIngredient(single);
        return items.write(single, ingredient.washed(), true);
    }

    public static ItemStack processWashCycle(JavaPlugin plugin, ItemStack rawItem) {
        if (!(plugin instanceof ArcaneBrewery brewery))
            throw new IllegalArgumentException("ArcaneAlchemy ingredient catalog is required");
        return processWashCycle(brewery.ingredientItems(), rawItem);
    }

    public static int getPurity(IngredientItemAdapter items, ItemStack item) {
        return items.metadata().purity(item);
    }
    public static int getPurity(JavaPlugin plugin, ItemStack item) {
        return new IngredientMetadataCodec(plugin).purity(item);
    }
    public static boolean hasLegacyWashedMarker(JavaPlugin plugin, ItemStack item) {
        return new IngredientMetadataCodec(plugin).legacyWashed(item);
    }
    public static boolean isPremiumGrain(JavaPlugin plugin, ItemStack item) {
        return new IngredientMetadataCodec(plugin).isPremium(item);
    }
}
