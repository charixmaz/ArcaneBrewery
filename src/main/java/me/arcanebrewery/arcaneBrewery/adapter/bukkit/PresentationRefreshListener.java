package me.arcanebrewery.arcaneBrewery.adapter.bukkit;

import me.arcanebrewery.arcaneBrewery.MessageConfig;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public final class PresentationRefreshListener implements Listener {
    private final JavaPlugin plugin;
    private final FinalDrinkMetadata drinks;
    private final me.arcanebrewery.arcaneBrewery.application.discovery.DiscoveryQueryRuntime queries;
    public PresentationRefreshListener(JavaPlugin plugin,me.arcanebrewery.arcaneBrewery.application.discovery.DiscoveryQueryRuntime queries) { this.plugin=plugin;this.queries=queries; drinks=new FinalDrinkMetadata(plugin); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { refresh(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onHeld(PlayerItemHeldEvent event) { refresh(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onInteract(PlayerInteractEvent event) { refresh(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) plugin.getServer().getScheduler().runTask(plugin,() -> refresh(player));
    }
    public void refresh(Player player) {
        var requested=new java.util.HashSet<String>();
        for (ItemStack item:player.getInventory().getContents()) {
            refresh(item);
            if(item==null || !item.hasItemMeta()) continue;
            var producer=drinks.producer(item.getItemMeta());var id=drinks.formulation(item.getItemMeta());
            if(producer.isEmpty() || id.isEmpty() || !requested.add(producer.get()+":"+id.get().value())) continue;
            // Query off-thread; apply only to the same still-identifiable bottle on the server thread.
            queries.personalName(producer.get(),id.get()).whenComplete((name,failure)->{
                if(failure!=null || name.isEmpty() || !plugin.isEnabled()) return;
                plugin.getServer().getScheduler().runTask(plugin,()->{
                    if(!player.isOnline()) return;
                    for(var candidate:player.getInventory().getContents()) {
                        if(candidate==null || !candidate.hasItemMeta()) continue;
                        var meta=candidate.getItemMeta();
                        if(drinks.producer(meta).equals(producer) && drinks.formulation(meta).equals(id)) {
                            meta.setDisplayName("§d§l"+name.get()+"§r");ElixirPresentation.refresh(meta,drinks);candidate.setItemMeta(meta);
                        }
                    }
                });
            });
        }
    }
    public boolean refresh(ItemStack item) {
        if (item==null || item.getType()!=Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        try {
            boolean changed=ElixirPresentation.refresh(meta,drinks);
            if (!changed) {
                var data=meta.getPersistentDataContainer();
                if (data.has(new NamespacedKey(plugin,"drink_format"))) return false;
                String stage=data.getOrDefault(new NamespacedKey(plugin,"brew_stage"),PersistentDataType.STRING,"");
                var fermented=new FermentationBatchMetadata(plugin).read(meta);
                if (fermented.isPresent()) {
                    boolean ready=fermented.get().status()==me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState.Status.COMPLETED;
                    meta.setDisplayName(ready?MessageConfig.title("cultivated-base"):"§6§lCultivating Brewing Base§r");
                    meta.setLore(List.of(ready?"§7Ready for infusion or maturation.§r":"§7Cultivation continues while offline.§r")); changed=true;
                } else if (data.has(new NamespacedKey(plugin,"distillery_format"))) {
                    meta.setDisplayName(MessageConfig.title("refined-base"));
                    meta.setLore(List.of("§7Insert in a Cultivation Barrel; sneak-click for controls.§r")); changed=true;
                } else if (stage.equals("INDUSTRIAL_METHANOL")) {
                    meta.setDisplayName(MessageConfig.title("cleanser"));
                    meta.setLore(List.of("§7For the existing cask cleaning control.§r","§cUnsafe solution: do not consume.§r")); changed=true;
                } else if (stage.equals("REFINED_INDUSTRIAL_ALCOHOL")) {
                    meta.setDisplayName("§d§lRefined Catalyst Base§r");
                    meta.setLore(List.of("§7For the Cleansing Solution producer.§r")); changed=true;
                } else if ((stage.startsWith("WORT_") || stage.startsWith("PREMIUM_WORT_"))
                        && meta.hasDisplayName() && ChatColor.stripColor(meta.getDisplayName()).startsWith("Unfermented")) {
                    meta.setDisplayName("§d§lLegacy Brewing Base§r");
                    meta.setLore(List.of("§7Compatible legacy base; process metadata retained.§r")); changed=true;
                }
            }
            if (changed) { meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP); PresentationStyle.apply(meta); item.setItemMeta(meta); }
            return changed;
        } catch (RuntimeException malformed) { return false; } // Never rewrite untrusted metadata.
    }
}
