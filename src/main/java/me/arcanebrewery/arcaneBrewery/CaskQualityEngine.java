package me.arcanebrewery.arcaneBrewery;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class CaskQualityEngine {

    private static final Random random = new Random();

    public static int calculateHarvestStars(JavaPlugin plugin, ItemStack potion, String physicalWoodType, Block barrelBlock) {
        if (potion == null || !potion.hasItemMeta()) return 0; // 0-Star Default fail

        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        NamespacedKey slotTimeKey = new NamespacedKey(plugin, "slot_start_time");
        NamespacedKey recipeTypeKey = new NamespacedKey(plugin, "brew_stage");
        NamespacedKey pityCounterKey = new NamespacedKey(plugin, "cask_batch_pity");

        String recipeKey = meta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "WORT_BEER");

        // --- 0-STAR CRITICAL FAIL CONDITIONS ---
        if (recipeKey.equals("RUINED_UNWASHED")) return 0;

        BreweryData.RecipeProfile profile = BreweryData.RECIPES.get(recipeKey);
        if (profile == null) return 0;

        // Wood Mismatch Penalty -> Forces 0-Star Slop output instantly
        if (!physicalWoodType.equalsIgnoreCase(profile.woodRequired)) {
            return 0;
        }

        long startTime = meta.getPersistentDataContainer().getOrDefault(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        int targetTime = profile.baseDuration;

        // Under-aged or Over-aged failures collapse down to 0-Stars or low tiers
        if (elapsed < targetTime) return 2; // Under-aged remains 2-star draft
        if (elapsed > (targetTime + 15)) return 0; // Over-aged drops completely to 0-star slop

        // --- THE HARDCORE BATCH PITY TRACKER MATRIX ---
        int currentBatchCount = 0;
        TileState tileState = null;

        if (barrelBlock != null && barrelBlock.getState() instanceof TileState state) {
            tileState = state;
            currentBatchCount = tileState.getPersistentDataContainer().getOrDefault(pityCounterKey, PersistentDataType.INTEGER, 0);
        }

        // Check if the current cask state has hit the 10-batch milestone trigger
        if (currentBatchCount >= 10 && recipeKey.equalsIgnoreCase("PREMIUM_WORT_BEER")) {
            // Reset block container pity tracking keys back to baseline zero entry state
            if (tileState != null) {
                tileState.getPersistentDataContainer().set(pityCounterKey, PersistentDataType.INTEGER, 0);
                tileState.update();
            }
            return 6; // Guaranteed Mythic unlock tier approved!
        }

        // --- THE NEW ULTRA HARDCORE RNG SPREAD SYSTEM ---
        double roll = random.nextDouble() * 100; // Translate values from 0.0-100.0 flat matrix percentages

        if (roll <= 50.0) {
            return 1; // 50% Chance: 1-Star ("Meh Quality")
        } else if (roll <= 90.0) {
            return 2; // 40% Chance: 2-Star ("Average Brew")
        } else if (roll <= 95.0) {
            return 3; // 5% Chance: 3-Star ("Fine Reserve")
        } else if (roll <= 98.0) {
            return 4; // 3% Chance: 4-Star ("Masterwork")
        } else if (roll <= 99.5) {
            return 5; // 1.5% Chance: 5-Star ("Grandmaster Flawless")
        } else {
            return 6; // 0.5% Chance: Rare manual 6-Star Mythic roll
        }
    }

    // Increments the permanent block batch data count inside container memory
    public static void incrementBatchCounter(JavaPlugin plugin, Block barrelBlock) {
        if (barrelBlock == null || !(barrelBlock.getState() instanceof TileState tileState)) return;

        NamespacedKey pityCounterKey = new NamespacedKey(plugin, "cask_batch_pity");
        int current = tileState.getPersistentDataContainer().getOrDefault(pityCounterKey, PersistentDataType.INTEGER, 0);

        tileState.getPersistentDataContainer().set(pityCounterKey, PersistentDataType.INTEGER, current + 1);
        tileState.update();
    }
}