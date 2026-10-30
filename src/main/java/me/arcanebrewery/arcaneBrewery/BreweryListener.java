package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ProductionLayout;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.CultivationStructure;


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
                event.getPlayer().sendMessage("§c[ArcaneAlchemy] This barrel contains an active cultivation batch. Remove or finish it before breaking the barrel.");
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
    }

    private final class Controls implements me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.MachineGuiHolder {
        final org.bukkit.block.Block core;
        Inventory inventory;
        Controls(org.bukkit.block.Block core) { this.core=core; }
        public Inventory getInventory() { return inventory; }
    }
    private org.bukkit.NamespacedKey controlsKey(org.bukkit.block.Block core) {
        return new org.bukkit.NamespacedKey("arcanebrewery","cultivation_controls_"+(core.getX()&15)+"_"+core.getY()+"_"+(core.getZ()&15));
    }
    private FermentationSettings settings(org.bukkit.block.Block core) {
        String saved=core.getChunk().getPersistentDataContainer().get(controlsKey(core),org.bukkit.persistence.PersistentDataType.STRING);
        if(saved==null) return new FermentationSettings(FermentationSettings.DEFAULT.yeastOrCatalyst(),22,timing.cultivationSeconds(),100,0);
        String[] parts=saved.split(",");
        if(parts.length!=3) throw new IllegalArgumentException("invalid controls");
        var culture=me.arcanebrewery.arcaneBrewery.domain.process.BrewingCulture.of(parts[0]);
        double temperature=Double.parseDouble(parts[1]);long duration=Long.parseLong(parts[2]);
        if(!Double.isFinite(temperature)||temperature<10||temperature>36||(temperature-10)%2!=0||duration<1||duration>604800) throw new IllegalArgumentException("invalid cultivation controls");
        return new FermentationSettings(culture.id(),temperature,duration,100,0);
    }
    private void save(org.bukkit.block.Block core,FermentationSettings s) {
        core.getChunk().getPersistentDataContainer().set(controlsKey(core),org.bukkit.persistence.PersistentDataType.STRING,
            s.yeastOrCatalyst()+","+s.temperatureCelsius()+","+s.durationSeconds());
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void controls(org.bukkit.event.player.PlayerInteractEvent event) {
        var block=event.getClickedBlock();
        if(block==null || !event.getPlayer().isSneaking() || event.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND
            || event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || !(block.getState() instanceof Barrel)
            ) return;
        if(access.recognized(block)!=Kind.CULTIVATION){
            if(access.recognized(block)!=Kind.CASK)event.getPlayer().sendMessage(MessageConfig.prefix()+"§eCultivation structure: face the Barrel horizontally and place a wooden Fence directly behind it at the same height. Right-click for inventory; sneak-right-click for controls.§r");
            return;
        }
        event.setCancelled(true);
        if(!access.authorize(event.getPlayer(),block,Kind.CULTIVATION,false)) return;
        try {
            access.open(event.getPlayer(),block,Kind.CULTIVATION,()->{
                var view=new Controls(block);view.inventory=org.bukkit.Bukkit.createInventory(view,ProductionLayout.SIZE,"§5§lCultivation Controls§r");
                paint(view);return view.inventory;
            });
        } catch(RuntimeException invalid) { event.getPlayer().sendMessage(MessageConfig.prefix()+"§cControls unavailable; contents retained.§r"); }
    }
    private void paint(Controls view) {
        ProductionLayout.CULTIVATION.frame(view.inventory);
        var s=settings(view.core);var c=me.arcanebrewery.arcaneBrewery.domain.process.BrewingCulture.of(s.yeastOrCatalyst());
        control(view,0,Material.WHEAT,"Culture: "+c.name(),List.of("Built-in starter library; no external plugin or item needed.",
            "Experiment with temperature and duration for your mixture.","Click for next culture; resets controls to starting values."));
        control(view,2,Material.BLAZE_POWDER,"Temperature: "+s.temperatureCelsius()+" C",List.of("Click: 10–36 C in 2 C steps.","Compare results from different settings."));
        control(view,4,Material.CLOCK,"Duration: "+s.durationSeconds()+" s",List.of("Click: 30–300 s in 30 s steps.","Captured batches keep their original settings."));
        control(view,8,Material.LIME_DYE,"Start Cultivation",List.of("Uses all eligible refined bases in this barrel.","Right-click the Barrel for its inventory; sneak-right-click for controls.","A wooden Fence must be directly behind the Barrel.","Active batch settings are locked; progress continues offline."));
        ProductionLayout.CULTIVATION.relocate(view.inventory);
        ProductionLayout.label(view.inventory,16,Material.BARREL,"Physical barrel inventory", "Insert refined bases in the Barrel itself.","Shift-click a base here to insert it safely.","Selected settings are captured when you start.");
        ProductionLayout.label(view.inventory,20,Material.CLOCK,"Cultivation duration", "Configured duration: "+s.durationSeconds()+" seconds", "Active batches retain their captured settings.","Offline time counts; this is not an ideal-time hint.");
    }
    private void control(Controls view,int slot,Material material,String title,List<String> lore) {
        var item=new ItemStack(material);var meta=item.getItemMeta();me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems.mark(meta);meta.setDisplayName("§b§l"+title+"§r");
        meta.setLore(lore.stream().map(l->"§7"+l+"§r").toList());me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta);
        item.setItemMeta(meta);view.inventory.setItem(slot,item);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void controlClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Controls view)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player) || !access.active(view.inventory,player.getUniqueId())
            || !(view.core.getState() instanceof Barrel barrel)
            || (event.getClick()!=org.bukkit.event.inventory.ClickType.LEFT && event.getClick()!=org.bukkit.event.inventory.ClickType.RIGHT && !event.isShiftClick())) return;
        try {
            refresh(barrel.getInventory());
            boolean active=false;
            for(var item:barrel.getInventory().getContents()) if(item!=null && item.hasItemMeta()) {
                var state=fermentationMetadata.read(item.getItemMeta());
                if(state.isPresent() && state.get().status()==FermentationBatchState.Status.FERMENTING) active=true;
            }
            if(active) { player.sendMessage(MessageConfig.prefix()+"§eWait for active cultivation to complete; captured controls are locked.§r");return; }
            if(event.isShiftClick()){
                if(event.getClickedInventory()!=player.getInventory())return;
                var data=barrel.getInventory().getContents();var slots=java.util.stream.IntStream.range(0,data.length).toArray();
                var plan=me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ShiftTransfers.plan(event.getCurrentItem(),data,slots,i->1,(i,item)->access.canonicalBase(item));
                barrel.getInventory().setContents(plan.destination());event.setCurrentItem(plan.remainder());return;
            }
            var s=settings(view.core);
            int slot=event.getClickedInventory()==view.inventory?ProductionLayout.CULTIVATION.logical(event.getSlot()):-1;
            if(slot==8) {
                if(!CultivationStructure.valid(view.core)){player.sendMessage(MessageConfig.prefix()+"§eRestore a wooden Fence directly behind the horizontal Barrel before starting. Existing bottles and progress are retained.§r");return;}
                boolean started=startEligible(barrel.getInventory(),s);
                player.sendMessage(MessageConfig.prefix()+(started?"§aCultivation started; settings captured.§r":"§eInsert unprocessed refined bases first.§r"));return;
            }
            if(slot==0) {
                var all=me.arcanebrewery.arcaneBrewery.domain.process.BrewingCulture.values();
                var next=all[(me.arcanebrewery.arcaneBrewery.domain.process.BrewingCulture.of(s.yeastOrCatalyst()).ordinal()+1)%all.length];
                s=new FermentationSettings(next.id(),next.temperature(),next.seconds(),100,0);
            } else if(slot==2) s=new FermentationSettings(s.yeastOrCatalyst(),s.temperatureCelsius()>=36?10:s.temperatureCelsius()+2,s.durationSeconds(),100,0);
            else if(slot==4) s=new FermentationSettings(s.yeastOrCatalyst(),s.temperatureCelsius(),s.durationSeconds()>=300?30:s.durationSeconds()+30,100,0);
            else return;
            save(view.core,s);paint(view);
        } catch(RuntimeException invalid) { player.sendMessage(MessageConfig.prefix()+"§cCultivation state could not be updated; inputs retained.§r"); }
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void controlDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Controls) event.setCancelled(true);
    }

    private boolean startEligible(Inventory inventory, FermentationSettings selected) {
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
                    selected, System.currentTimeMillis());
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
