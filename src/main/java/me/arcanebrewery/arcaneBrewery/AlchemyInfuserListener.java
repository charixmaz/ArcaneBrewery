package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyPreparationMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Locale;

public final class AlchemyInfuserListener implements Listener {
    private final FermentationBatchMetadata fermentation;
    private final AlchemyPreparationMetadata preparation;
    private final AlchemyPreparationService service = new AlchemyPreparationService();

    public AlchemyInfuserListener(JavaPlugin plugin) {
        this.fermentation = new FermentationBatchMetadata(plugin);
        this.preparation = new AlchemyPreparationMetadata(plugin);
    }

    @EventHandler
    public void onInfuse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE) return;

        ItemStack base = event.getPlayer().getInventory().getItemInMainHand();
        ItemStack catalystStack = event.getPlayer().getInventory().getItemInOffHand();
        if (base.getType() != Material.POTION || !(base.getItemMeta() instanceof PotionMeta meta)) return;

        var fermented = fermentation.read(meta);
        if (fermented.isEmpty() || fermented.get().status() != FermentationBatchState.Status.COMPLETED) {
            event.getPlayer().sendMessage("§c[ArcaneBrewery] Only completed cultivated bases can be infused.");
            return;
        }
        if (preparation.read(meta).isPresent()) {
            event.getPlayer().sendMessage("§c[ArcaneBrewery] This batch already contains an alchemical catalyst.");
            return;
        }
        if (catalystStack.getType() == Material.AIR) {
            event.getPlayer().sendMessage("§7[ArcaneBrewery] Hold a supported catalyst in your off-hand.");
            return;
        }

        String external = "minecraft:" + catalystStack.getType().name().toLowerCase(Locale.ROOT);
        var catalyst = BuiltInAlchemyCatalysts.resolveExternal(external);
        if (catalyst.isEmpty()) {
            event.getPlayer().sendMessage("§c[ArcaneBrewery] That item is not a recognized catalyst.");
            return;
        }

        event.setCancelled(true);
        preparation.write(meta, service.prepare(catalyst.get()));

        var lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<String>();
        lore.add("§dAlchemy Catalyst: §f" + catalyst.get().displayName());
        meta.setLore(lore);
        base.setItemMeta(meta);

        if (catalystStack.getAmount() > 1) catalystStack.setAmount(catalystStack.getAmount() - 1);
        else event.getPlayer().getInventory().setItemInOffHand(new ItemStack(Material.AIR));

        event.getPlayer().sendMessage("§d[ArcaneBrewery] Infused with " + catalyst.get().displayName() + ".");
    }
}
