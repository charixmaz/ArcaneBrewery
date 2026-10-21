package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery.DistilleryBatchMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;

import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;

/**
 * Canonical fermentation bridge. Only V1.5 distillery outputs participate;
 * arbitrary legacy barrel contents no longer create fermentation state.
 */
public final class BreweryListener implements Listener {
    private final JavaPlugin plugin;
    private final DistilleryBatchMetadata distilleryMetadata;
    private final FermentationBatchMetadata fermentationMetadata;
    private final FermentationService fermentation = new FermentationService();

    public BreweryListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.distilleryMetadata = new DistilleryBatchMetadata(plugin);
        this.fermentationMetadata = new FermentationBatchMetadata(plugin);
    }

    @EventHandler
    public void onBarrelOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Barrel barrel)) return;
        if (refresh(event.getInventory())) barrel.update();
    }

    @EventHandler
    public void onBarrelClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Barrel barrel)) return;
        boolean changed = refresh(event.getInventory());
        boolean started = startEligible(event.getInventory());
        if (changed || started) barrel.update();

        if (started) {
            event.getPlayer().sendMessage("§6[Brewery] Fermentation has begun. The batch will continue while you are away.");
        }
    }

    private boolean startEligible(Inventory inventory) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) continue;
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null || fermentationMetadata.read(meta).isPresent()) continue;

            var distillery = distilleryMetadata.read(meta);
            if (distillery.isEmpty() || !"DISTILLED_BASE".equals(distilleryMetadata.stage(meta))) continue;

            var source = distillery.get();
            if (source.status() != me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryBatchState.Status.COMPLETED) {
                continue;
            }

            var state = fermentation.start(UUID.randomUUID(), source.draft(),
                    FermentationSettings.DEFAULT, System.currentTimeMillis());
            fermentationMetadata.write(meta, state);
            meta.setDisplayName("§6Fermenting Brewing Base");
            meta.setLore(List.of(
                    "§7Stage: §6Fermenting",
                    "§7Catalyst: §fWild Yeast",
                    "§7Progress continues while offline.",
                    "",
                    "§8Canonical process data stored internally."
            ));
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            changed = true;
        }
        return changed;
    }

    private boolean refresh(Inventory inventory) {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) continue;
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) continue;

            var stored = fermentationMetadata.read(meta);
            if (stored.isEmpty()) continue;
            var state = stored.get();
            if (state.status() == FermentationBatchState.Status.FERMENTING && state.ready(now)) {
                var completed = state.complete(now);
                fermentationMetadata.write(meta, completed);
                meta.setDisplayName("§aFermented Brewing Base");
                meta.setLore(List.of(
                        "§7Stage: §aFermented",
                        "§7Ready for aging.",
                        "",
                        "§8Canonical process data stored internally."
                ));
                item.setItemMeta(meta);
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }
}
