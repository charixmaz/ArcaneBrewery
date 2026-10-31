package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ProductionLayout;


import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.AgingBatchMetadata;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class CaskVisualEngine {
    private CaskVisualEngine() { }
    public static void updateCaskInterface(JavaPlugin plugin,Inventory inv,Block core,String owner) {
        ProductionLayout.CASK.frame(inv);
        pane(inv,8,Material.BOOK,"Maturation",List.of("§7Owner: §f"+owner+"§r","§7Record actual harvest times. No cleaning required.§r"));
        for (int lane=0;lane<4;lane++) {
            int slot=lane*2; ItemStack bottle=inv.getItem(slot);
            String state="EMPTY"; Material type=Material.GRAY_STAINED_GLASS_PANE;
            var lore=new ArrayList<String>(); lore.add("§7Storage: §fLane "+(lane+1)+"§r");
            if (bottle!=null && bottle.getType()==Material.POTION && bottle.getItemMeta()!=null) {
                try {
                    var meta=bottle.getItemMeta(); var aging=new AgingBatchMetadata(plugin).read(meta);
                    long started=meta.getPersistentDataContainer().getOrDefault(new NamespacedKey("arcanebrewery","slot_start_time"),PersistentDataType.LONG,System.currentTimeMillis());
                    long elapsed=Math.max(0,(System.currentTimeMillis()-started)/1000);
                    long target=120;
                    lore.add("§7Wood: §f"+me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.CaskStructure.wood(core)+"§r");
                    var harvest=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.CaskHarvestMetadata("arcanebrewery").read(meta);
                    if (aging.isPresent()) { elapsed=aging.get().elapsedSeconds(harvest.map(h->h.harvestedAt()).orElseGet(System::currentTimeMillis)); target=aging.get().maturationSeconds(); }
                    else {
                        pane(inv,slot+1,Material.GRAY_STAINED_GLASS_PANE,"Lane "+(lane+1)+" — PRESERVED INPUT",
                                List.of("§7Stored input retained without processing.§r","§7Withdraw it; new batches need a cultivated base.§r"));
                        continue;
                    }
                    state="AGING"; type=Material.BLUE_STAINED_GLASS_PANE;
                    lore.add("§7Elapsed: §b"+elapsed+"s§r");
                    if(harvest.isPresent()) {
                        state="HARVEST RETAINED"; type=Material.YELLOW_STAINED_GLASS_PANE;
                        lore.add("§7Harvest time preserved while discovery is saved.§r");
                        lore.add("§7If saving failed, withdraw again to retry safely.§r");
                    } else lore.add("§7Experiment with harvest times and record the results.§r");
                    lore.add("§7Click or shift-click the bottle to withdraw.§r");
                    lore.add("§eEarly withdrawal: Unfinished Elixir; no discovery.§r");
                } catch (RuntimeException corrupt) { state="DATA LOCKED"; type=Material.BARRIER; lore.add("§eStored bottle retained for inspection.§r"); }
            } else if (bottle!=null) { state="PRESERVED INPUT"; lore.add("§7Withdraw this unsupported legacy item.§r"); }
            else lore.add("§7Place a cultivated base in the slot to the left.§r");
            pane(inv,slot+1,type,"Lane "+(lane+1)+" — "+state,lore);
        }
        ProductionLayout.CASK.relocate(inv);
        ProductionLayout.label(inv,19,Material.CLOCK,"Elapsed time only", "Each lane records actual time spent aging.","No target, countdown, readiness or ideal time is shown.");
        ProductionLayout.label(inv,25,Material.GLASS_BOTTLE,"Harvest", "Click or shift-click a stored bottle to withdraw.","Early withdrawal may produce an unfinished elixir.","Compare actual harvests and record your results.");
    }
    private static void pane(Inventory inv,int slot,Material material,String name,List<String> lore) {
        var item=new ItemStack(material); var meta=item.getItemMeta(); me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems.mark(meta); meta.setDisplayName("§b§l"+name+"§r"); meta.setLore(lore); me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta); item.setItemMeta(meta); inv.setItem(slot,item);
    }
}
