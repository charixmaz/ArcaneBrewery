package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
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

public class CauldronWasherEngine implements Listener {
    private final IngredientItemAdapter ingredients;

    public CauldronWasherEngine(IngredientItemAdapter ingredients) {
        this.ingredients = java.util.Objects.requireNonNull(ingredients);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIngredientWash(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Bukkit has separate block/item decisions; never override a prior protection denial.
        if (event.useInteractedBlock() == Event.Result.DENY || event.useItemInHand() == Event.Result.DENY) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.CAULDRON
                && clicked.getType() != Material.WATER_CAULDRON) return;

        Player player = event.getPlayer();
        var inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (!WashableIngredients.MATERIALS.contains(held.getType()) || DisplayOnlyItems.test(held)) return;

        // Suppress vanilla eating/interaction only after honoring existing decisions.
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (clicked.getType() == Material.CAULDRON) {
            player.sendMessage("§c[Washer] This cauldron is empty! Fill it with water before washing ingredients.");
            return;
        }
        var originalWater = clicked.getBlockData();
        if (!(originalWater instanceof Levelled water) || water.getLevel() < 1) return;

        ItemStack washed;
        ItemStack[] next;
        var before = java.util.Arrays.stream(inventory.getStorageContents())
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        try {
            if (IngredientPurityEngine.getPurity(ingredients, held) >= 100) {
                player.sendMessage("§a[Washer] This ingredient is already 100% pure!");
                return;
            }
            washed = IngredientPurityEngine.processWashCycle(ingredients, held);
            var delivery = WashingInventory.plan(before, inventory.getHeldItemSlot(), washed);
            if (delivery.isEmpty()) {
                player.sendMessage("§e[Washer] Make inventory room for the washed ingredient first. Nothing was consumed.");
                return;
            }
            next = delivery.orElseThrow();
        } catch (IllegalArgumentException invalid) {
            player.sendMessage("§c[Washer] This ingredient cannot be washed; nothing was consumed.");
            return;
        }

        try {
            inventory.setStorageContents(next);
            if (water.getLevel() == 1) clicked.setType(Material.CAULDRON);
            else {
                var lowered = (Levelled) water.clone();
                lowered.setLevel(water.getLevel() - 1);
                clicked.setBlockData(lowered);
            }
        } catch (RuntimeException failure) {
            // Keep both resources on a failed API update, then let the server log the cause.
            try { inventory.setStorageContents(before); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            try { clicked.setBlockData(originalWater); } catch (RuntimeException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }

        player.getWorld().playSound(clicked.getLocation(), "minecraft:item.bucket.empty", 1.0f, 1.3f);
        int purity = IngredientPurityEngine.getPurity(ingredients, washed);
        player.sendMessage(purity == 100 ? "§a[Washer] ✔ Washing complete. Ingredient purity: 100%."
                : "§e[Washer] ♻ Cycle complete. Purity increased to " + purity + "%. Wash again.");
    }
}
