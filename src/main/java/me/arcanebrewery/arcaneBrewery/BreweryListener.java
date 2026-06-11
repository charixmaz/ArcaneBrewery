package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class BreweryListener implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey fermentTimeKey;
    private final NamespacedKey ingredientCountKey;
    private final NamespacedKey isFermentingKey;

    public BreweryListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.fermentTimeKey = new NamespacedKey(plugin, "ferment_time");
        this.ingredientCountKey = new NamespacedKey(plugin, "ingredient_count");
        this.isFermentingKey = new NamespacedKey(plugin, "is_fermenting");
    }

    @EventHandler
    public void onBarrelClose(InventoryCloseEvent event) {
        // Check if the closed inventory belongs to a Barrel block
        if (!(event.getInventory().getHolder() instanceof Barrel barrel)) return;

        Inventory inv = event.getInventory();
        int ingredientCount = 0;
        boolean hasBaseLiquidContainer = false;

        // Scan the barrel slots
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;

            Material type = item.getType();

            // Detect any items that act as a bottle, liquid base, or juice container
            if (type == Material.HONEY_BOTTLE ||
                    type == Material.POTION ||
                    type == Material.SPLASH_POTION ||
                    type == Material.LINGERING_POTION ||
                    type == Material.GLASS_BOTTLE ||
                    type == Material.MILK_BUCKET ||
                    type == Material.BOWL) {

                hasBaseLiquidContainer = true;
            } else {
                // Count any other items (Wheat, Berries, Sugar, etc.) as brewing ingredients
                ingredientCount += item.getAmount();
            }
        }

        // Get the block data storage
        var data = barrel.getPersistentDataContainer();

        // If it has a valid bottle/liquid and at least 3 ingredients, start fermenting!
        if (hasBaseLiquidContainer && ingredientCount >= 3) {
            if (!data.has(isFermentingKey, PersistentDataType.BOOLEAN)) {
                data.set(isFermentingKey, PersistentDataType.BOOLEAN, true);
                data.set(fermentTimeKey, PersistentDataType.INTEGER, 0); // 0 nights passed
                barrel.update(); // Save changes to the block status

                event.getPlayer().sendMessage("§6[Brewery] The mixture bubbles... Fermentation has begun! It will take 6 nights.");
            }
        }
    }

    @EventHandler
    public void onCauldronInteract(PlayerInteractEvent event) {
        // We only care if the player RIGHT-CLICKS a block
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Check if the clicked block is a Cauldron
        if (clickedBlock.getType() == Material.CAULDRON ||
                clickedBlock.getType() == Material.WATER_CAULDRON) {

            Player player = event.getPlayer();

            // Check the multi-block structure: Is there fire underneath?
            Block blockUnderneath = clickedBlock.getRelative(BlockFace.DOWN);

            if (blockUnderneath.getType() == Material.FIRE || blockUnderneath.getType() == Material.CAMPFIRE) {
                player.sendMessage("§a[Brewery] This cauldron is hot and ready for brewing!");
            } else {
                player.sendMessage("§c[Brewery] The cauldron needs a heat source (Fire or Campfire) underneath it!");
            }
        }
    }
}