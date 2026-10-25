package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import org.bukkit.Material;
import java.util.Set;

/** Materials with a normal cauldron washing interaction. Not the entire ingredient registry. */
public final class WashableIngredients {
    private WashableIngredients() { }
    public static final Set<Material> MATERIALS = Set.of(Material.WHEAT, Material.SUGAR_CANE,
            Material.APPLE, Material.GOLDEN_APPLE, Material.POTATO, Material.SWEET_BERRIES, Material.HONEYCOMB);
}
