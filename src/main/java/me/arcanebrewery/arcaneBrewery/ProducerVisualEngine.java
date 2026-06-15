package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ProducerVisualEngine {

    public static void updateProducerInterface(JavaPlugin plugin, Inventory inv, Block coreBlock) {
        NamespacedKey slotTimeKey = new NamespacedKey(plugin, "slot_start_time");

        // Set static automated separator walls
        int[] decorativeSlots = {1, 3, 5, 7};
        for (int slot : decorativeSlots) {
            setCustomPane(inv, slot, Material.GRAY_STAINED_GLASS_PANE, "§8⚙ Automated Condenser",
                    List.of("§7Processes refined industrial compounds into Methanol fluid."));
        }

        // Slot 8 serves as our static ventilation control readout panel
        setCustomPane(inv, 8, Material.CHARCOAL, "§a⚙ Ventilation Input Pure",
                List.of("§7Status: §2OPERATIONAL", "§7Industrial pressure balanced."));

        // Evaluate active fermentation lanes (Slots 0, 2, 4, 6)
        int[] activeInputSlots = {0, 2, 4, 6};
        for (int slot : activeInputSlots) {
            ItemStack item = inv.getItem(slot);
            int indicatorSlot = slot + 1; // The glass pane directly to its right

            if (item == null || item.getType() == Material.AIR) {
                setCustomPane(inv, indicatorSlot, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Refinery Line Idle",
                        List.of("§7Drop Refined Industrial Alcohol here to begin condensation."));
                continue;
            }

            if (item.getType() == Material.POTION && item.hasItemMeta()) {
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(slotTimeKey, PersistentDataType.LONG)) {

                    long startTime = meta.getPersistentDataContainer().getOrDefault(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                    long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;

                    // TESTING THRESHOLD: Set to 10 seconds for rapid deployment testing.
                    // Change this variable to 300 when you are ready for the final 5-minute batches!
                    long targetTime = 10;

                    if (elapsedSeconds < targetTime) {
                        // Display active condensation line ticker
                        setCustomPane(inv, indicatorSlot, Material.BLUE_STAINED_GLASS_PANE, "§b🧪 Gas Condensation Active", List.of(
                                "§7Status: §bPressurizing Molecules...",
                                "§7Progress: §e" + elapsedSeconds + "s / " + targetTime + "s",
                                "",
                                "§4⚠ DO NOT EXTRACT EARLY"
                        ));
                    } else {
                        // Display completed ready state trigger
                        setCustomPane(inv, indicatorSlot, Material.GREEN_STAINED_GLASS_PANE, "§a✔ CHEMICAL REFINE COMPLETE", List.of(
                                "§7Status: §aLiquid Methanol Settled",
                                "§7Purity: §f99.8% Concentrate",
                                "",
                                "§e[SHIFT-CLICK] §7potion bottle left to extract product."
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