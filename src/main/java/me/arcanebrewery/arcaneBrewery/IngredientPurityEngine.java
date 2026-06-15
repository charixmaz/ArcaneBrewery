package me.arcanebrewery.arcaneBrewery;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class IngredientPurityEngine {

    public static ItemStack processWashCycle(JavaPlugin plugin, ItemStack rawItem) {
        NamespacedKey purityKey = new NamespacedKey(plugin, "ingredient_purity");
        ItemStack washedItem = rawItem.clone();
        washedItem.setAmount(1);

        ItemMeta meta = washedItem.getItemMeta();
        if (meta == null) return washedItem;

        // Fetch current purity, default to 0 if completely unwashed
        int currentPurity = meta.getPersistentDataContainer().getOrDefault(purityKey, PersistentDataType.INTEGER, 0);

        // Increase purity by 25% per wash cycle, capping at 100%
        int newPurity = Math.min(100, currentPurity + 25);
        meta.getPersistentDataContainer().set(purityKey, PersistentDataType.INTEGER, newPurity);

        String color = newPurity == 100 ? "§2" : (newPurity >= 50 ? "§e" : "§c");
        String status = newPurity == 100 ? "Fully Cleaned" : "Partially Washed";

        meta.setDisplayName("§aWashed " + formatName(rawItem.getType().name()));

        List<String> lore = new ArrayList<>();
        lore.add("§7Status: " + color + status);
        lore.add("§7Purity Level: " + color + newPurity + "%");
        lore.add("");

        if (newPurity < 100) {
            lore.add("§8§oRequires more washing cycles.");
        } else {
            lore.add("§d§oReady for industrial refinery processing lines.");
        }

        meta.setLore(lore);
        washedItem.setItemMeta(meta);

        return washedItem;
    }

    public static int getPurity(JavaPlugin plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        NamespacedKey purityKey = new NamespacedKey(plugin, "ingredient_purity");
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(purityKey, PersistentDataType.INTEGER, 0);
    }

    private static String formatName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            formatted.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return formatted.toString().trim();
    }
}