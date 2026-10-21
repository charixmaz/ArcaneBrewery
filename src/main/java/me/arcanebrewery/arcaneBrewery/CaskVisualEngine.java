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

        // --- CONDENSER VALVE PORT (SLOT 8) ---
        if (isInfected) {
            setCustomPane(inv, 8, Material.HOPPER, "§4§l☣ CHEMICAL INJECTOR VALVE", List.of(
                    "§7Status: §cBLOCKED BY CONTAMINATION",
                    "",
                    "§e[DRAG & DROP] §fIndustrial Methanol Fluid",
                    "§7directly over this slot to flush the core lines."
            ));
        } else {
            setCustomPane(inv, 8, Material.CHARCOAL, "§a⚙ Ventilation Input Pure", List.of(
                    "§7Status: §2OPERATIONAL",
                    "§7Atmospheric inner pressure balanced."
            ));
        }

        // Dividers
        int[] dividers = {1, 3, 5, 7};
        for (int div : dividers) {
            if (isInfected) {
                setCustomPane(inv, div, Material.LIME_STAINED_GLASS_PANE, "§c☣ Clogged Line", List.of("§7Purify via the injector input."));
            } else {
                setCustomPane(inv, div, Material.GRAY_STAINED_GLASS_PANE, "§8| Separator Line", List.of("§7Isolates fluid pressure."));
            }
        }

        for (int slot : storageSlots) {
            ItemStack bottle = inv.getItem(slot);
            int indicatorSlot = slot + 1;

            if (bottle != null && bottle.getType().name().endsWith("_GLASS_PANE")) continue;

            // FIX: Draw the barrier on the INDICATOR pane, leaving the real potion in the slot alone!
            if (isInfected) {
                setCustomPane(inv, indicatorSlot, Material.BARRIER, "§c☣ UNIT LOCKED", List.of("§7Methanol flush required."));
                continue;
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

                    NamespacedKey agingFormatKey = new NamespacedKey(plugin, "aging_format");
                    NamespacedKey agingStartKey = new NamespacedKey(plugin, "aging_started_at");
                    if (meta.getPersistentDataContainer().has(agingFormatKey, PersistentDataType.INTEGER)
                            && meta.getPersistentDataContainer().has(agingStartKey, PersistentDataType.LONG)) {
                        long canonicalStart = meta.getPersistentDataContainer().getOrDefault(
                                agingStartKey, PersistentDataType.LONG, System.currentTimeMillis());
                        long canonicalElapsed = Math.max(0, (System.currentTimeMillis() - canonicalStart) / 1000);
                        long targetTime = me.arcanebrewery.arcaneBrewery.application.aging.AgingBalance.targets().agingSeconds();
                        if (canonicalElapsed < targetTime) {
                            setCustomPane(inv, indicatorSlot, Material.BLUE_STAINED_GLASS_PANE,
                                    "§b● Canonical Aging Active", List.of(
                                            "§7Progress: §e" + canonicalElapsed + "s / " + targetTime + "s",
                                            "§7Quality is process-driven, not randomly rolled."
                                    ));
                        } else {
                            setCustomPane(inv, indicatorSlot, Material.GREEN_STAINED_GLASS_PANE,
                                    "§a● MATURED — READY TO BOTTLE", List.of(
                                            "§7Shift-click potion left to finish the batch.",
                                            "§7Further aging may alter final quality."
                                    ));
                        }
                        continue;
                    }

                    BreweryData.RecipeProfile profile = BreweryData.RECIPES.get(recipeKey);

                    if (recipeKey.equals("RUINED_UNWASHED") || profile == null) {
                        setCustomPane(inv, indicatorSlot, Material.BROWN_STAINED_GLASS_PANE, "§c● BATCH SPOILED", List.of("§7Unwashed toxic compounds detected."));
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