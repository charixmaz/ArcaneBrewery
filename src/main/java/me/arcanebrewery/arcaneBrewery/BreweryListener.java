package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery.DistilleryBatchMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.BukkitMachineAccess;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity.Kind;

import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.block.BlockBreakEvent;
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
    private final BukkitMachineAccess access;
    private final ProcessingTimes timing;

    public BreweryListener(JavaPlugin plugin,BukkitMachineAccess access,ProcessingTimes timing) {
        this.plugin = plugin;
        this.distilleryMetadata = new DistilleryBatchMetadata(plugin);
        this.fermentationMetadata = new FermentationBatchMetadata(plugin);
        this.access=access; this.timing=timing;
    }


    @EventHandler(ignoreCancelled=true)
    public void onFermentationBarrelBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getState() instanceof Barrel barrel)) return;

        for (ItemStack item : barrel.getInventory().getContents()) {
            if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) continue;
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) continue;
            java.util.Optional<FermentationBatchState> stored;
            try { stored=fermentationMetadata.read(meta); }
            catch (RuntimeException corrupt) { event.setCancelled(true); return; }
            if (stored.isPresent() && stored.get().status() == FermentationBatchState.Status.FERMENTING) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c[ArcaneBrewery] This barrel contains an active cultivation batch. Remove or finish it before breaking the barrel.");
                return;
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onBarrelOpen(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Barrel barrel) || !(event.getPlayer() instanceof Player player)) return;
        if (access.recognized(barrel.getBlock())!=Kind.CULTIVATION || !access.permitted(player,barrel.getBlock())) return;
        try { refresh(event.getInventory()); }
        catch (RuntimeException corrupt) {
            event.setCancelled(true); player.sendMessage(MessageConfig.prefix()+"§cCultivation data is invalid; stored bottles were retained.§r"); return;
        }
        player.sendMessage(MessageConfig.prefix()+"§7Cultivation Barrel owner: §f"+access.ownerLabel(barrel.getBlock())+"§r");
        plugin.getServer().getScheduler().runTask(plugin,() -> {
            if (player.getOpenInventory().getTopInventory()==event.getInventory()) player.getOpenInventory().setTitle(MessageConfig.title("cultivation"));
        });
    }

    @EventHandler
    public void onBarrelClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Barrel barrel) || !(event.getPlayer() instanceof Player player)) return;
        if (access.recognized(barrel.getBlock())!=Kind.CULTIVATION
                || !access.authorize(player,barrel.getBlock(),Kind.CULTIVATION,true)) return;
        refresh(event.getInventory());
        boolean started = startEligible(event.getInventory());

        if (started) {
            event.getPlayer().sendMessage(MessageConfig.prefix()+"§aCultivation has begun. Progress continues while you are away.§r");
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
                    new FermentationSettings(FermentationSettings.DEFAULT.yeastOrCatalyst(),22.0,timing.cultivationSeconds(),85.0,10.0), System.currentTimeMillis());
            fermentationMetadata.write(meta, state);
            meta.setDisplayName("§6§lCultivating Brewing Base§r");
            meta.setLore(List.of(
                    "§7Stage: §6Cultivating§r",
                    "§7Duration: §f"+state.settings().durationSeconds()+"s§r",
                    "§7Progress continues while offline.§r"
            ));
            me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta); item.setItemMeta(meta);
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
            if (state.status()==FermentationBatchState.Status.FERMENTING && !state.ready(now)) {
                long elapsed=Math.max(0,(now-state.startedAtEpochMillis())/1000);
                meta.setDisplayName("§6§lCultivating Brewing Base§r");
                meta.setLore(List.of("§7Status: §bPROCESSING§r","§7Elapsed: §f"+elapsed+"s§r",
                        "§7Remaining: §f"+Math.max(0,state.settings().durationSeconds()-elapsed)+"s§r","§7Progress continues while offline.§r"));
                me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta); item.setItemMeta(meta); inventory.setItem(slot,item); changed=true;
            }
            if (state.status() == FermentationBatchState.Status.FERMENTING && state.ready(now)) {
                var completed = state.complete(now);
                fermentationMetadata.write(meta, completed);
                meta.setDisplayName(MessageConfig.title("cultivated-base"));
                meta.setLore(List.of(
                        "§7Stage: §aCultivated§r",
                        "§7Ready for infusion or maturation.§r",
                        "",
                        "§8Canonical process data stored internally."
                ));
                me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta); item.setItemMeta(meta);
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }
}
