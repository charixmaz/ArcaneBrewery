package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientItemAdapter;

public class CauldronWasherEngine implements Listener {

    private final IngredientItemAdapter ingredients;

    public CauldronWasherEngine(IngredientItemAdapter ingredients) {
        this.ingredients = java.util.Objects.requireNonNull(ingredients);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIngredientWash(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material blockType = clicked.getType();
        if (blockType != Material.CAULDRON && blockType != Material.WATER_CAULDRON) return;

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Material heldType = heldItem.getType();

        if (heldType == Material.WHEAT || heldType == Material.SUGAR_CANE || heldType == Material.APPLE ||
                heldType == Material.GOLDEN_APPLE || heldType == Material.POTATO || heldType == Material.SWEET_BERRIES || heldType == Material.HONEYCOMB) {

            // ISSUE 3 FIX: Explicitly deny the interaction at the core packet level to stop the eating animation!
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);

            if (blockType == Material.CAULDRON) {
                player.sendMessage("§c[Washer] This cauldron is empty! Fill it with water before washing ingredients.");
                return;
            }

            // Verify maximum purity hasn't already been reached
            int currentPurity = IngredientPurityEngine.getPurity(ingredients, heldItem);
            if (currentPurity >= 100) {
                player.sendMessage("§a[Washer] This ingredient is already 100% pure!");
                return;
            }

            // Validate/copy the ingredient before consuming water or the held stack.
            ItemStack washedResult = IngredientPurityEngine.processWashCycle(ingredients, heldItem);

            if (clicked.getBlockData() instanceof Levelled cauldronData) {
                int currentLevel = cauldronData.getLevel();
                if (currentLevel <= 1) {
                    clicked.setType(Material.CAULDRON);
                } else {
                    cauldronData.setLevel(currentLevel - 1);
                    clicked.setBlockData(cauldronData);
                }
            } else {
                return;
            }

            if (heldItem.getAmount() > 1) {
                heldItem.setAmount(heldItem.getAmount() - 1);
                player.getInventory().setItemInMainHand(heldItem);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            player.getInventory().addItem(washedResult);
            player.getWorld().playSound(clicked.getLocation(), org.bukkit.Sound.ITEM_BUCKET_EMPTY, 1.0f, 1.3f);

            int newPurity = IngredientPurityEngine.getPurity(ingredients, washedResult);
            if (newPurity == 100) {
                player.sendMessage("§a[Washer] ✔ Sanitization complete. Contaminants fully removed!");
            } else {
                player.sendMessage("§e[Washer] ♻ Cycle complete. Purity increased to " + newPurity + "%. Wash again.");
            }
        }
    }
}
