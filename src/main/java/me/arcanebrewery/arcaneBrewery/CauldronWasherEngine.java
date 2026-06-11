package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class CauldronWasherEngine implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey washedItemKey;

    public CauldronWasherEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.washedItemKey = new NamespacedKey(plugin, "washed_ingredient");
    }

    @EventHandler
    public void onCauldronWash(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.WATER_CAULDRON) return;

        // Ensure glass block is right next to it
        if (!hasAdjacentGlass(clicked)) return;

        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        if (held == null || held.getType() == Material.AIR) return;

        Levelled cauldronData = (Levelled) clicked.getBlockData();
        if (cauldronData.getLevel() <= 0) return; // Cauldron empty

        // FIX: Evaluates the raw item block type so custom items can be washed smoothly!
        if (isBrewableMaterial(held.getType())) {
            event.setCancelled(true);

            // Drain cauldron water layer by 1
            cauldronData.setLevel(cauldronData.getLevel() - 1);
            if (cauldronData.getLevel() == 0) {
                clicked.setType(Material.CAULDRON);
            } else {
                clicked.setBlockData(cauldronData);
            }

            // Audio effects
            clicked.getWorld().playSound(clicked.getLocation(), Sound.ENTITY_BOAT_PADDLE_WATER, 1.0f, 1.2f);

            // Construct washed item while fully preserving preexisting custom metadata tags (like Premium Grain)!
            ItemStack washedItem = held.clone();
            washedItem.setAmount(1);
            ItemMeta meta = washedItem.getItemMeta();

            if (meta != null) {
                // Keep old lore if it exists, otherwise instantiate a clean array list
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                meta.setDisplayName("§bWashed " + capitalizeWord(held.getType().name()));
                lore.add("§7Cleaned and ready for custom distillery processing.");
                meta.setLore(lore);

                // Inject the validation tag required by the distillery stand
                meta.getPersistentDataContainer().set(washedItemKey, PersistentDataType.STRING, "TRUE");
                washedItem.setItemMeta(meta);
            }

            // Decrease held stack size
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);

            // Toss clean item output to player
            clicked.getWorld().dropItemNaturally(player.getLocation(), washedItem);
            player.sendMessage("§a[Brewery] Ingredient washed successfully!");
        }
    }

    private boolean hasAdjacentGlass(Block cauldron) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            if (cauldron.getRelative(face).getType() == Material.GLASS) {
                return true;
            }
        }
        return false;
    }

    private boolean isBrewableMaterial(Material mat) {
        return mat == Material.WHEAT || mat == Material.SUGAR || mat == Material.SUGAR_CANE ||
                mat == Material.APPLE || mat == Material.KELP || mat == Material.MELON_SLICE ||
                mat == Material.CACTUS || mat == Material.BROWN_MUSHROOM || mat == Material.COCOA_BEANS ||
                mat == Material.BLAZE_POWDER || mat == Material.CHORUS_FRUIT || mat == Material.GLISTERING_MELON_SLICE ||
                mat == Material.GLOWSTONE_DUST || mat == Material.WITHER_SKELETON_SKULL || mat == Material.PRISMARINE_CRYSTALS ||
                mat == Material.AMETHYST_SHARD || mat == Material.NETHERITE_SCRAP || mat == Material.ECHO_SHARD ||
                mat == Material.SHULKER_SHELL;
    }

    private String capitalizeWord(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase().replace("_", " ");
    }
}