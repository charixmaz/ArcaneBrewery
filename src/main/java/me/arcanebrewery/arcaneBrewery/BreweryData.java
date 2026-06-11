package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import java.util.HashMap;
import java.util.Map;

public class BreweryData {

    public static class RecipeProfile {
        public final String name;
        public final String woodRequired;
        public final int baseDuration; // Ideal target time in seconds

        public RecipeProfile(String name, String woodRequired, int baseDuration) {
            this.name = name;
            this.woodRequired = woodRequired;
            this.baseDuration = baseDuration;
        }
    }

    public static final Map<String, RecipeProfile> RECIPES = new HashMap<>();

    static {
        // --- TIER 1 RECIPES (Oak / Birch / Jungle / Acacia / Spruce) ---
        RECIPES.put("WORT_BEER", new RecipeProfile("Traditional Barley Ale", "OAK", 120));
        RECIPES.put("WORT_MEAD", new RecipeProfile("Sweet Meadow Mead", "BIRCH", 150));
        RECIPES.put("WORT_GROG", new RecipeProfile("Sailor's Grog", "JUNGLE", 180));
        RECIPES.put("WORT_CIDER", new RecipeProfile("Farmer's Hard Cider", "OAK", 140));
        RECIPES.put("WORT_PIT_BEER", new RecipeProfile("Miner's Pit Beer", "DARK_OAK", 120));
        RECIPES.put("WORT_TONIC", new RecipeProfile("Wandering Tonic", "ACACIA", 160));
        RECIPES.put("WORT_CACTUS", new RecipeProfile("Badlands Cactus Wash", "ACACIA", 150));
        RECIPES.put("WORT_MOONSHINE", new RecipeProfile("Swamp-Water Moonshine", "DARK_OAK", 200));
        RECIPES.put("WORT_TODDY", new RecipeProfile("Snowy Peak Toddy", "SPRUCE", 130));
        RECIPES.put("WORT_GLAZED_MEAD", new RecipeProfile("Honey-Glazed Mead", "BIRCH", 180));

        // --- TIER 2 RECIPES (Spruce / Dark Oak / Acacia / Crimson / Mangrove) ---
        RECIPES.put("WORT_VODKA", new RecipeProfile("Glacial Vodka", "SPRUCE", 240));
        RECIPES.put("PREMIUM_WORT_BEER", new RecipeProfile("Lord's Premium Stout", "DARK_OAK", 300));
        RECIPES.put("WORT_TEQUILA", new RecipeProfile("Cactus Tequila", "ACACIA", 260));
        RECIPES.put("WORT_PUMPKIN", new RecipeProfile("Golden Pumpkin Ale", "OAK", 220));
        RECIPES.put("WORT_ABSINTHE", new RecipeProfile("Crimson Absinthe", "CRIMSON", 320));
        RECIPES.put("WORT_SAKI", new RecipeProfile("Bamboo Saki", "JUNGLE", 210));
        RECIPES.put("WORT_SCHNAPPS", new RecipeProfile("Glow-Berry Schnapps", "BIRCH", 240));
        RECIPES.put("WORT_SLIME", new RecipeProfile("Slime-Malt Whiskey", "MANGROVE", 280));
        RECIPES.put("WORT_CHORUS", new RecipeProfile("Chorus-Pop Ale", "JUNGLE", 250));
        RECIPES.put("WORT_GIN", new RecipeProfile("Deep-Sea Kelp Gin", "SPRUCE", 300));

        // --- TIER 3 RECIPES (Mangrove / Warped / Cherry / Pale Oak) ---
        RECIPES.put("WORT_WHISKEY", new RecipeProfile("Netherfire Whiskey", "MANGROVE", 400));
        RECIPES.put("WORT_BLINK", new RecipeProfile("Ender's Blink Wine", "WARPED", 500));
        RECIPES.put("WORT_GOLD", new RecipeProfile("Miner's Liquid Gold", "JUNGLE", 450));
        RECIPES.put("WORT_BRANDY", new RecipeProfile("Dragon's Breath Brandy", "DARK_OAK", 600));
        RECIPES.put("WORT_WITHER", new RecipeProfile("Wither's Dark Vintage", "DARK_OAK", 540));
        RECIPES.put("WORT_RUM", new RecipeProfile("Ocean Monument Rum", "SPRUCE", 480));
        RECIPES.put("WORT_AMETHYST", new RecipeProfile("Amethyst Geode Absinthe", "CHERRY", 550));
        RECIPES.put("WORT_DEBRIS", new RecipeProfile("Ancient Debris Brandy", "PALE_OAK", 720));
        RECIPES.put("WORT_WARDEN", new RecipeProfile("Warden's Soul-Skulk Ale", "DARK_OAK", 600));
        RECIPES.put("WORT_SHULKER", new RecipeProfile("Shulker's Anti-Gravity Bourbon", "PALE_OAK", 645));
    }

    public static String getWoodFromBlock(Material material) {
        String name = material.name();
        name = name.replace("_STAIRS", "").replace("_SLAB", "").replace("_PLANKS", "").replace("_LOG", "").replace("_WOOD", "");
        return name;
    }
}