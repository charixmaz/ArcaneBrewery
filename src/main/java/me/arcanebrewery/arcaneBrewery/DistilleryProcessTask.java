package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DistilleryProcessTask {

    private static final Map<Block, BukkitRunnable> runningBrews = new HashMap<>();

    public static void triggerBoilingSequence(JavaPlugin plugin, Inventory inv, Block standBlock) {
        ItemStack ingredient = inv.getItem(4);
        ItemStack[] bottles = {inv.getItem(0), inv.getItem(1), inv.getItem(2)};

        if (ingredient == null || ingredient.getType() == Material.AIR || !hasBottles(bottles)) return;
        if (runningBrews.containsKey(standBlock)) return;

        NamespacedKey startTimeKey = new NamespacedKey(plugin, "distill_start_time");

        if (inv.getItem(8) != null && inv.getItem(8).getType() == Material.REDSTONE_TORCH) {
            ItemMeta matchMeta = ingredient.getItemMeta();
            if (matchMeta != null && !matchMeta.getPersistentDataContainer().has(startTimeKey, PersistentDataType.LONG)) {
                matchMeta.getPersistentDataContainer().set(startTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                ingredient.setItemMeta(matchMeta);
            }
        }

        BukkitRunnable brewTask = new BukkitRunnable() {
            int countdown = 10;

            @Override
            public void run() {
                countdown--;

                standBlock.getWorld().spawnParticle(Particle.SMOKE, standBlock.getLocation().add(0.5, 0.8, 0.5), 5, 0.1, 0.1, 0.1, 0.02);
                standBlock.getWorld().spawnParticle(Particle.SMALL_FLAME, standBlock.getLocation().add(0.5, 0.4, 0.5), 2, 0.05, 0.05, 0.05, 0.01);

                inv.setItem(8, new ItemStack(Material.ORANGE_STAINED_GLASS_PANE));

                if (countdown <= 0) {
                    executeBoilConversion(plugin, inv);
                    runningBrews.remove(standBlock);
                    inv.setItem(8, new ItemStack(Material.REDSTONE_TORCH));
                    this.cancel();
                }
            }
        };

        brewTask.runTaskTimer(plugin, 0L, 20L);
        runningBrews.put(standBlock, brewTask);
    }

    public static void evaluateOfflineBoiling(JavaPlugin plugin, Inventory inv, Block standBlock) {
        ItemStack ingredient = inv.getItem(4);
        if (ingredient == null || !ingredient.hasItemMeta()) return;

        NamespacedKey startTimeKey = new NamespacedKey(plugin, "distill_start_time");
        if (!ingredient.getItemMeta().getPersistentDataContainer().has(startTimeKey, PersistentDataType.LONG)) return;

        long startTime = ingredient.getItemMeta().getPersistentDataContainer().getOrDefault(startTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        if (elapsed >= 10) {
            executeBoilConversion(plugin, inv);
        } else {
            triggerBoilingSequence(plugin, inv, standBlock);
        }
    }

    private static void executeBoilConversion(JavaPlugin plugin, Inventory inv) {
        ItemStack ingredient = inv.getItem(4);
        if (ingredient == null) return;

        NamespacedKey washedItemKey = new NamespacedKey(plugin, "washed_ingredient");
        NamespacedKey premiumGrainKey = new NamespacedKey(plugin, "premium_grain_tag");

        boolean isWashed = ingredient.hasItemMeta() && ingredient.getItemMeta().getPersistentDataContainer().has(washedItemKey, PersistentDataType.STRING);
        boolean isPremium = ingredient.hasItemMeta() && ingredient.getItemMeta().getPersistentDataContainer().has(premiumGrainKey, PersistentDataType.STRING);

        String recipeType = "RUINED_UNWASHED";
        String finalName = "§8Ruined Unwashed Slop Base";
        Material ingType = ingredient.getType();

        if (isWashed) {
            if (ingType == Material.WHEAT) {
                if (isPremium) {
                    recipeType = "PREMIUM_WORT_BEER";
                    finalName = "§b§lExclusive Unfermented Beer Wort";
                } else {
                    recipeType = "WORT_BEER";
                    finalName = "§eUnfermented Beer Wort";
                }
            } else if (ingType == Material.SUGAR) {
                recipeType = "WORT_VODKA";
                finalName = "§fUnfermented Vodka Mash";
            } else if (ingType == Material.SUGAR_CANE) {
                recipeType = "WORT_GROG";
                finalName = "§7Unfermented Grog Mash";
            } else if (ingType == Material.APPLE) {
                recipeType = "WORT_CIDER";
                finalName = "§aUnfermented Apple Cider Base";
            } else if (ingType == Material.KELP) {
                recipeType = "WORT_PIT_BEER";
                finalName = "§8Unfermented Pit Mash";
            } else if (ingType == Material.MELON_SLICE) {
                recipeType = "WORT_TONIC";
                finalName = "§dUnfermented Tonic Mash";
            } else if (ingType == Material.CACTUS) {
                recipeType = "WORT_CACTUS";
                finalName = "§2Unfermented Cactus Mash";
            } else if (ingType == Material.BROWN_MUSHROOM) {
                recipeType = "WORT_MOONSHINE";
                finalName = "§5Unfermented Moonshine Slop";
            } else if (ingType == Material.COCOA_BEANS) {
                recipeType = "WORT_TODDY";
                finalName = "§cUnfermented Toddy Mixture";
            } else if (ingType == Material.BLAZE_POWDER) {
                recipeType = "WORT_WHISKEY";
                finalName = "§6Unfermented Whiskey Mash";
            } else if (ingType == Material.CHORUS_FRUIT) {
                recipeType = "WORT_BLINK";
                finalName = "§dUnfermented Blink Extract";
            } else if (ingType == Material.GLISTERING_MELON_SLICE) {
                recipeType = "WORT_GOLD";
                finalName = "§eUnfermented Liquid Gold Wort";
            } else if (ingType == Material.GLOWSTONE_DUST) {
                recipeType = "WORT_BRANDY";
                finalName = "§eUnfermented Brandy Blend";
            } else if (ingType == Material.WITHER_SKELETON_SKULL) {
                recipeType = "WORT_WITHER";
                finalName = "§8Unfermented Dark Core";
            } else if (ingType == Material.PRISMARINE_CRYSTALS) {
                recipeType = "WORT_RUM";
                finalName = "§3Unfermented Deep Sea Mash";
            } else if (ingType == Material.AMETHYST_SHARD) {
                recipeType = "WORT_AMETHYST";
                finalName = "§dUnfermented Crystal Extract";
            } else if (ingType == Material.NETHERITE_SCRAP) {
                recipeType = "WORT_DEBRIS";
                finalName = "§4Unfermented Crust Blend";
            } else if (ingType == Material.ECHO_SHARD) {
                recipeType = "WORT_WARDEN";
                finalName = "§1Unfermented Echo Slop";
            } else if (ingType == Material.SHULKER_SHELL) {
                recipeType = "WORT_SHULKER";
                finalName = "§dUnfermented Gravity Mash";
            }
        }

        ingredient.setAmount(ingredient.getAmount() - 1);
        inv.setItem(4, ingredient);

        processItemsOutput(plugin, inv, recipeType, finalName);
    }

    private static void processItemsOutput(JavaPlugin plugin, Inventory inv, String recipeType, String finalName) {
        NamespacedKey itemStageKey = new NamespacedKey(plugin, "brew_stage");

        for (int i = 0; i < 3; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && (item.getType() == Material.POTION || item.getType() == Material.GLASS_BOTTLE)) {
                item.setType(Material.POTION);
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(finalName);
                    meta.setLore(recipeType.equals("RUINED_UNWASHED") ?
                            List.of("§cThis base was brewed with filthy ingredients!", "§7It can never yield true star quality.") :
                            List.of("§7Place this inside a locked 9-Slot Sign structure to ferment."));

                    // CRITICAL FIX: Explicitly stamps the recipe string key into container memory!
                    meta.getPersistentDataContainer().set(itemStageKey, PersistentDataType.STRING, recipeType);
                    item.setItemMeta(meta);
                    inv.setItem(i, item);
                }
            }
        }
    }

    private static boolean hasBottles(ItemStack[] items) {
        for (ItemStack item : items) {
            if (item != null && (item.getType() == Material.POTION || item.getType() == Material.GLASS_BOTTLE)) return true;
        }
        return false;
    }
}