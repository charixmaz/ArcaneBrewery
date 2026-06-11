package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class ProducerVisualEngine {

    public static void updateProducerInterface(JavaPlugin plugin, Inventory inv, Block coreBlock) {
        // Draw custom mechanical border interfaces
        int[] decorativeSlots = {1, 3, 5, 7, 8};
        for (int slot : decorativeSlots) {
            setCustomPane(inv, slot, Material.GRAY_STAINED_GLASS, "§8⚙ Automated Condenser", List.of("§7Processes methane compounds into Methanol fluid."));
        }

        int[] activeInputSlots = {0, 2, 4, 6};
        for (int slot : activeInputSlots) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                setCustomPane(inv, slot + 1, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Refinery Line Idle", List.of("§7Drop base inputs to ignite fuel injection lines."));
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