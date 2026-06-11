package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class CaskVisualEngine {

    public static void updateCaskInterface(JavaPlugin plugin, Inventory inv, Block barrelBlock) {
        NamespacedKey slotTimeKey = new NamespacedKey(plugin, "slot_start_time");
        NamespacedKey recipeTypeKey = new NamespacedKey(plugin, "brew_stage");
        NamespacedKey spoilTimeKey = new NamespacedKey(plugin, "cask_spoil_timestamp");

        boolean isInfected = CaskHygieneEngine.checkInfectionStatus(plugin, barrelBlock);
        int[] storageSlots = {0, 2, 4, 6};

        // Draw Core Ventilation Border Item
        if (isInfected) {
            setCustomPane(inv, 8, Material.SLIME_BLOCK, "§c§l☣ AIR SYSTEM CRITICAL", List.of("§7Spoiled fungal mold spores have blocked the tubes."));
        } else {
            setCustomPane(inv, 8, Material.NETHERITE_SCRAP, "§d§l⚡ Cask Ventilation Core", List.of("§7Maintains atmospheric pressure."));
        }

        for (int slot : storageSlots) {
            ItemStack bottle = inv.getItem(slot);
            int indicatorSlot = slot + 1;

            if (bottle != null && bottle.getType().name().endsWith("_GLASS_PANE")) {
                continue;
            }

            // If completely contaminated, everything turns into radioactive layout blocks
            // Inside your loop rules within CaskVisualEngine.java:

// --- RENDER SLOT 8 CHEMICAL INPUT VALVE HOOKS ---
            if (isInfected) {
                setCustomPane(inv, 8, Material.HOPPER, "§4§l☣ CHEMICAL INJECTOR VALVE", List.of(
                        "§7Status: §cBLOCKED BY CARBON",
                        "",
                        "§e[DRAG & DROP] §fPure Methanol Fluid",
                        "§7directly here to flush out infection lines."
                ));
            } else {
                setCustomPane(inv, 8, Material.CHARCOAL, "§a⚙ Ventilation Input Pure", List.of(
                        "§7Status: §2OPERATIONAL",
                        "§7Atmospheric pressure balanced."
                ));
            }

            if (bottle == null || bottle.getType() == Material.AIR) {
                setCustomPane(inv, indicatorSlot, Material.GRAY_STAINED_GLASS_PANE, "§8空 §7Storage Available", List.of("§7Drop unfermented bottles directly to left."));
                continue;
            }

            if (bottle.getType() == Material.POTION) {
                PotionMeta meta = (PotionMeta) bottle.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(slotTimeKey, PersistentDataType.LONG)) {

                    long startTime = meta.getPersistentDataContainer().getOrDefault(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    String recipeKey = meta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "UNKNOWN");

                    BreweryData.RecipeProfile profile = BreweryData.RECIPES.get(recipeKey);

                    if (recipeKey.equals("RUINED_UNWASHED") || profile == null) {
                        setCustomPane(inv, indicatorSlot, Material.BROWN_STAINED_GLASS_PANE, "§c● BATCH SPOILED", List.of("§7Unwashed or toxic compounds detected."));
                        continue;
                    }

                    int targetTime = profile.baseDuration;

                    if (elapsed < targetTime) {
                        setCustomPane(inv, indicatorSlot, Material.BLUE_STAINED_GLASS_PANE, "§b● Fermentation Active", List.of(
                                "§7Product: §f" + profile.name,
                                "§7Progress: §e" + elapsed + "s / " + targetTime + "s"
                        ));
                    } else if (elapsed >= targetTime && elapsed <= (targetTime + 15)) {
                        setCustomPane(inv, indicatorSlot, Material.GREEN_STAINED_GLASS_PANE, "§a● PEAK PURITY WINDOW", List.of("§7The sweet spot achieved. Shift-click potion left to bottle product."));
                    } else {
                        // RE-WRITTEN LOGIC: Bottle turned stagnant. Initialize the 10-minute fuse if missing!
                        if (barrelBlock != null && barrelBlock.getState() instanceof TileState state) {
                            if (!state.getPersistentDataContainer().has(spoilTimeKey, PersistentDataType.LONG)) {
                                state.getPersistentDataContainer().set(spoilTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                                state.update();
                            }
                        }

                        long spoilTimer = 0;
                        if (barrelBlock != null && barrelBlock.getState() instanceof TileState state) {
                            long startSpoil = state.getPersistentDataContainer().getOrDefault(spoilTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                            spoilTimer = 600 - ((System.currentTimeMillis() - startSpoil) / 1000);
                        }

                        setCustomPane(inv, indicatorSlot, Material.BLACK_STAINED_GLASS_PANE, "§8● STAGNANT ACME", List.of(
                                "§7Yeast carbonation collapsed.",
                                "§c☣ Contamination fuse ticking: §f" + Math.max(0, spoilTimer) + "s / 600s"
                        ));
                    }
                }
            }
        }
    }

    private static void setCustomPane(Inventory inv, int index, Material material, String name, List<String> lore) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            pane.setItemMeta(meta);
        }
        inv.setItem(index, pane);
    }
}