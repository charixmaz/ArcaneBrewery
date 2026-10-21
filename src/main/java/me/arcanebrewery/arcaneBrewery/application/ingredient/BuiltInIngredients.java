package me.arcanebrewery.arcaneBrewery.application.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import java.util.*;

/** Initial game-balance defaults, not chemistry. All composition values live here. */
public final class BuiltInIngredients {
    public static final String PREMIUM_GRAIN = "arcanebrewery:premium_grain";
    private BuiltInIngredients() {}

    public static IngredientRegistry registry() {
        List<IngredientDefinition> definitions = List.of(
                definition("wheat", "Wheat", 20, 80, 5, 10, 40, 0, 0),
                definition("sugar_cane", "Sugar Cane", 75, 10, 10, 5, 45, 0, 0),
                definition("sugar", "Sugar", 100, 0, 0, 0, 15, 0, 0),
                definition("apple", "Apple", 60, 5, 35, 5, 70, 0, 5),
                definition("golden_apple", "Golden Apple", 55, 5, 30, 5, 85, 0, 75),
                definition("potato", "Potato", 15, 90, 5, 8, 25, 12, 0),
                definition("sweet_berries", "Sweet Berries", 60, 5, 55, 10, 85, 0, 5),
                definition("honeycomb", "Honeycomb", 90, 0, 10, 0, 90, 0, 10),
                definition("kelp", "Kelp", 10, 10, 25, 45, 45, 5, 15),
                definition("melon_slice", "Melon", 65, 5, 15, 5, 65, 0, 5),
                definition("cactus", "Cactus", 30, 10, 45, 30, 45, 5, 5),
                definition("brown_mushroom", "Brown Mushroom", 5, 20, 15, 40, 50, 20, 25),
                definition("red_mushroom", "Red Mushroom", 5, 15, 20, 50, 45, 40, 30),
                definition("cocoa_beans", "Cocoa", 20, 25, 25, 80, 95, 5, 10),
                definition("blaze_powder", "Blaze Powder", 0, 0, 10, 70, 50, 35, 90),
                definition("chorus_fruit", "Chorus Fruit", 45, 10, 30, 15, 80, 10, 80),
                definition("glistering_melon_slice", "Glistering Melon", 45, 5, 10, 5, 80, 0, 65),
                definition("glowstone_dust", "Glowstone", 0, 0, 5, 55, 45, 25, 90),
                definition("prismarine_crystals", "Prismarine Crystals", 0, 0, 20, 40, 35, 10, 70),
                definition("amethyst_shard", "Amethyst", 0, 0, 0, 10, 60, 5, 85),
                definition("netherite_scrap", "Netherite Scrap", 0, 0, 0, 90, 5, 80, 75),
                definition("echo_shard", "Echo Shard", 0, 0, 10, 65, 65, 45, 100),
                definition("shulker_shell", "Shulker Shell", 0, 5, 15, 40, 45, 20, 85),
                definition("wither_skeleton_skull", "Wither Skeleton Skull", 0, 0, 35, 95, 10, 95, 100),
                new IngredientDefinition(new IngredientProfile(PREMIUM_GRAIN, 30, 85, 5, 5, 70, 0, 20),
                        "Exclusive Brewer's Grain", true, new IngredientModifiers(3, 3))
        );
        var mappings = new TreeMap<String, String>();
        definitions.stream().filter(d -> d.id().startsWith("minecraft:")).forEach(d -> mappings.put(d.id(), d.id()));
        return new IngredientRegistry(definitions, mappings);
    }

    private static IngredientDefinition definition(String key, String name, double sugar, double starch,
            double acid, double bitterness, double aroma, double toxicity, double affinity) {
        return new IngredientDefinition(new IngredientProfile("minecraft:" + key, sugar, starch,
                acid, bitterness, aroma, toxicity, affinity), name, false, IngredientModifiers.NONE);
    }
}
