package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemySlotTransfer;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity.Kind;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

/** Single authoritative inventory per extractor. All edits and completion persist
 * together before changing a cursor; decorative slots never enter storage. */
public final class DistilleryMenuHandler implements Listener, AutoCloseable {
    private static final int[] INPUTS = {0,1,2,4,5};
    private final JavaPlugin plugin;
    private final BukkitMachineAccess access;
    private final ProcessingTimes timing;
    private final MachineInventoryStorage storage;
    private final MachineIngredientExtractor ingredients;
    private final DistilleryBatchMetadata metadata;
    private final DistilleryStateCodec codec = new DistilleryStateCodec();
    private final Map<MachineLocation,Session> sessions = new HashMap<>();
    private final Map<MachineLocation,BukkitRunnable> tasks = new HashMap<>();
    private boolean closed;
    private static final class Holder implements MachineGuiHolder {
        Inventory inventory;
        public Inventory getInventory() { return inventory; }
    }
    private static final class Session {
        final Block core; final Inventory gui;
        byte[] raw; DistilleryBatchState state;
        int temperature=78, cuts=0, effectGeneration;
        Session(Block core,Inventory gui,byte[] raw,DistilleryBatchState state) {
            this.core=core; this.gui=gui; this.raw=raw; this.state=state;
        }
        boolean processing() { return state != null && state.status()==DistilleryBatchState.Status.PROCESSING; }
    }
    public DistilleryMenuHandler(JavaPlugin plugin,IngredientItemAdapter items,BukkitMachineAccess access,ProcessingTimes timing) {
        this.plugin=plugin; this.access=access; this.timing=timing;
        storage=new MachineInventoryStorage(plugin.getName().toLowerCase(Locale.ROOT),"distillery_inventory_storage","distillery_batch_state");
        ingredients=new MachineIngredientExtractor(items); metadata=new DistilleryBatchMetadata(plugin);
        access.occupancy(Kind.EXTRACTOR,this::occupied);
    }
    private MachineLocation key(Block core) { return new MachineLocation(core.getWorld().getUID(),core.getX(),core.getY(),core.getZ()); }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onOpen(PlayerInteractEvent event) {
        if (closed || event.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        Block clicked=event.getClickedBlock();
        if (!DistilleryStructureScanner.isValidDistillery(clicked)) return;
        Block core=DistilleryStructureScanner.getDistilleryStand(clicked);
        // Preserve an ordinary stand's pre-existing vanilla inventory. It must be
        // emptied normally before this first-use adoption can become custom GUI.
        if (access.identity(core).isEmpty() && core.getState() instanceof BrewingStand stand
                && !stand.getInventory().isEmpty()) {
            event.getPlayer().sendMessage(MessageConfig.prefix()+"§eEmpty the existing vanilla stand before adopting this extractor.§r"); return;
        }
        event.setCancelled(true);
        if (event.getHand()!=EquipmentSlot.HAND || !access.authorize(event.getPlayer(),core,Kind.EXTRACTOR,false)) return;
        try {
            Session session=load(core);
            access.open(event.getPlayer(),core,Kind.EXTRACTOR,() -> {
                recover(session);
                if (session.raw==null) commit(session,contents(session.gui),session.state);
                paint(session); return session.gui;
            });
        } catch (RuntimeException failure) { unavailable(event.getPlayer(),core); }
    }
    private Session load(Block core) {
        Session present=sessions.get(key(core)); if (present!=null) return present;
        var saved=storage.load(core); var holder=new Holder();
        Inventory gui=Bukkit.createInventory(holder,9,MessageConfig.title("extractor")); holder.inventory=gui;
        for (int i=0;i<9;i++) {
            if (!Set.of(0,1,2,4,5).contains(i) && saved.items()[i]!=null)
                throw new IllegalArgumentException("legacy machine has items in protected slots");
        }
        for (int slot:INPUTS) gui.setItem(slot,saved.items()[slot]);
        var envelope=saved.process().isBlank() || saved.process().startsWith("controls-v2:")?null:DistilleryProcessEnvelope.decode(saved.process());
        var state=envelope==null?null:envelope.state();
        if (state!=null && !state.machine().equals(key(core))) throw new IllegalArgumentException("wrong machine process");
        var session=new Session(core,gui,saved.raw(),state);
        session.effectGeneration=envelope==null?0:envelope.effectGeneration();
        if(saved.process().startsWith("controls-v2:")) {
            var parts=saved.process().split(":");
            if(parts.length!=3) throw new IllegalArgumentException("invalid extractor controls");
            session.temperature=Integer.parseInt(parts[1]);session.cuts=Integer.parseInt(parts[2]);
            if(session.temperature<60 || session.temperature>90 || (session.temperature-60)%2!=0 || session.cuts<0 || session.cuts>2) throw new IllegalArgumentException("invalid extractor controls");
        } else if(state!=null) {
            session.temperature=(int)state.draft().controls().temperatureCelsius();
            session.cuts=state.draft().controls().headsCutPercent()<10?1:state.draft().controls().headsCutPercent()>10?2:0;
        }
        sessions.put(key(core),session); return session;
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        Inventory gui=event.getView().getTopInventory(); if (!(gui.getHolder() instanceof Holder)) return;
        Session session=sessions.values().stream().filter(s -> s.gui==gui).findFirst().orElse(null);
        if (session==null || !(event.getWhoClicked() instanceof Player player)) { event.setCancelled(true); return; }
        if (!access.active(gui,player.getUniqueId()) || !DistilleryStructureScanner.isValidDistillery(session.core)) {
            event.setCancelled(true); return;
        }
        if(event.isShiftClick()){
            event.setCancelled(true);if(session.processing())return;
            try{me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ShiftTransfers.move(event,contents(gui),new int[]{0,1,2,3,4,5,6,7,8},new int[]{0,1,2,4,5},slot->slot==4||slot==5?8:1,(slot,item)->{
                if(slot!=4&&slot!=5)return bottleInput(item);
                try{return IngredientPurityEngine.getPurity(plugin,item)>0&&!ingredients.extract(new ItemStack[]{item}).ingredients().isEmpty();}catch(RuntimeException invalid){return false;}
            },next->commit(session,next,null));}catch(RuntimeException rejected){unavailable(player,session.core);}return;
        }
        if (event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT) { event.setCancelled(true); return; }
        if (event.getClickedInventory()!=gui) return;
        event.setCancelled(true); int slot=event.getSlot();
        if(session.processing()) return;
        if(slot==3 || slot==6 || slot==8) {
            try {
                if(slot==8) { tryStart(player,session);return; }
                if(slot==3) session.temperature=session.temperature>=90?60:session.temperature+2;
                if(slot==6) session.cuts=(session.cuts+1)%3;
                commit(session,contents(gui),null);
            } catch(RuntimeException failure) { unavailable(player,session.core);detach(session); }
            return;
        }
        if(Arrays.stream(INPUTS).noneMatch(value -> value==slot)) return;
        ItemStack cursor=player.getItemOnCursor();
        if (cursor!=null && !cursor.getType().isAir() && slot!=4 && slot!=5 && !bottleInput(cursor)) {
            player.sendMessage(MessageConfig.prefix()+"§eBottle inputs accept one empty glass bottle or unprocessed potion per slot.§r"); return;
        }
        try {
            var move=AlchemySlotTransfer.move(gui.getItem(slot),cursor,event.getClick()==ClickType.RIGHT,slot==4 || slot==5?8:1);
            ItemStack[] next=contents(gui); next[slot]=move.slot();
            commit(session,next,null); player.setItemOnCursor(move.cursor());
            paint(session);
        } catch (RuntimeException failure) { unavailable(player,session.core); detach(session); }
    }
    private boolean bottleInput(ItemStack item) {
        if (item.getType()==Material.GLASS_BOTTLE) return true;
        if (item.getType()!=Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
        var data=meta.getPersistentDataContainer();
        String ns=plugin.getName().toLowerCase(Locale.ROOT);
        return !data.has(new NamespacedKey(ns,"drink_format")) && !data.has(new NamespacedKey(ns,"fermentation_format"))
                && !data.has(new NamespacedKey(ns,"distillery_format"));
    }
    private void tryStart(Player player,Session session) {
        if (session.processing()) return;
        ItemStack ingredient=session.gui.getItem(4);
        if(ingredient==null) ingredient=session.gui.getItem(5);
        boolean bottle=false;
        for (int slot:new int[]{0,1,2}) {
            ItemStack item=session.gui.getItem(slot);
            if (item!=null && !item.getType().isAir()) {
                if (!bottleInput(item) || item.getAmount()!=1) return;
                bottle=true;
            }
        }
        if (ingredient==null || !bottle) { player.sendMessage(MessageConfig.prefix()+"§eSupply bottles in slots 1–3 and washed ingredients in slots 5–6.§r");return; }
        boolean legacy=legacyRefinement(session.gui);
        if (IngredientPurityEngine.getPurity(plugin,ingredient)==0 && !legacy) {
            player.sendMessage(MessageConfig.get("distillery_wash_first")); return;
        }
        if (legacy && (ingredient.getType()!=Material.GOLDEN_APPLE || session.gui.getItem(5)!=null)) {
            player.sendMessage(MessageConfig.get("distillery_needs_catalyst")); return;
        }
        try {
            for(int input:new int[]{4,5}) {
                var stack=session.gui.getItem(input);
                if(stack!=null && !legacy && stack.getAmount()>8) throw new IllegalArgumentException("mixing slot maximum is 8; split this retained stack first");
                if(stack!=null && IngredientPurityEngine.getPurity(plugin,stack)==0 && !legacy) throw new IllegalArgumentException("wash first");
            }
            var extracted=ingredients.extract(session.gui,4,5);
            if(!extracted.containers().isEmpty() || !extracted.baseLiquids().isEmpty()) throw new IllegalArgumentException("Only ingredients in mixing slots");
            double heads=session.cuts==1?2:session.cuts==2?15:10;
            var controls=new DistillationControls(session.temperature,heads,100-2*heads,heads,1);
            var draft=DistilleryProcessDraft.from(extracted,controls);
            draft=new DistilleryProcessDraft(draft.ingredientRatios(),draft.ingredientPurity(),legacy?"distillation":"distillation-v2",controls);
            var state=new DistilleryBatchState(DistilleryBatchState.VERSION,UUID.randomUUID(),key(session.core),draft,
                System.currentTimeMillis(),timing.extractionMillis(),DistilleryBatchState.Status.PROCESSING);
            session.effectGeneration=legacy?0:2;
            commit(session,contents(session.gui),state);
            if(plugin instanceof ArcaneBrewery brewery)brewery.reconcileMaterialInputs(player,new ItemStack[]{session.gui.getItem(4),session.gui.getItem(5)});
            schedule(session);
        } catch (IllegalArgumentException rejected) {
            player.sendMessage(MessageConfig.prefix()+"§eThis ingredient is not supported by the extractor.§r");
        }
    }
    private boolean legacyRefinement(Inventory inv) {
        for (int slot:new int[]{0,1,2}) {
            ItemStack item=inv.getItem(slot);
            if (item!=null && item.getItemMeta() instanceof PotionMeta meta && metadata.stage(meta).isBlank()
                    && "WORT_ETHANOL".equals(meta.getPersistentDataContainer().get(new NamespacedKey(plugin,"brew_stage"),PersistentDataType.STRING))) return true;
        }
        return false;
    }
    private void recover(Session session) {
        if (!session.processing() || !DistilleryStructureScanner.isValidDistillery(session.core)) return;
        if (session.state.ready(System.currentTimeMillis())) complete(session); else schedule(session);
    }
    private void schedule(Session session) {
        var key=key(session.core); if (tasks.containsKey(key)) return;
        var task=new BukkitRunnable() { public void run() {
            try {
                if (closed || !CaskStructure.loaded(session.core) || !DistilleryStructureScanner.isValidDistillery(session.core)) { detach(session); return; }
                if (!session.processing()) { stop(key); return; }
                if (session.state.ready(System.currentTimeMillis())) { complete(session); stop(key); }
                else paint(session);
                if (!session.processing() && session.gui.getViewers().isEmpty()) sessions.remove(key,session);
            } catch (RuntimeException corrupt) { plugin.getLogger().warning("Extractor locked at "+session.core.getLocation()); detach(session); }
        }};
        tasks.put(key,task); task.runTaskTimer(plugin,1,20);
    }
    private void complete(Session session) {
        if (!session.processing() || !session.state.ready(System.currentTimeMillis()) || !DistilleryStructureScanner.isValidDistillery(session.core)) return;
        ItemStack[] next=contents(session.gui);
        if (next[4]==null && next[5]==null) throw new IllegalStateException("missing locked ingredient");
        boolean v2=session.state.draft().preparationMethod().equals("distillation-v2");
        var current=DistilleryProcessDraft.from(ingredients.extract(session.gui,v2?new int[]{4,5}:new int[]{4}),session.state.draft().controls());
        current=new DistilleryProcessDraft(current.ingredientRatios(),current.ingredientPurity(),session.state.draft().preparationMethod(),current.controls());
        if (!current.equals(session.state.draft())) throw new IllegalStateException("locked ingredient changed");
        var completed=session.state.complete(System.currentTimeMillis());
        boolean legacy=legacyRefinement(session.gui);
        for (int slot:new int[]{0,1,2}) {
            ItemStack item=next[slot]; if (item==null) continue;
            if (!bottleInput(item) || item.getAmount()!=1) throw new IllegalStateException("invalid locked bottle");
            item.setType(Material.POTION); PotionMeta meta=(PotionMeta)item.getItemMeta();
            if (meta==null) throw new IllegalStateException("potion meta unavailable");
            if (legacy) {
                meta.setDisplayName("§d§lRefined Catalyst Base§r");
                meta.setLore(List.of("§7For the Cleansing Solution producer.§r"));
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"brew_stage"),PersistentDataType.STRING,"REFINED_INDUSTRIAL_ALCOHOL");
            } else {
                metadata.write(meta,completed,"DISTILLED_BASE");
                if(session.effectGeneration>0)new me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.EffectProfileMetadata(plugin.getName().toLowerCase(Locale.ROOT)).markGeneration(meta,session.effectGeneration);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"brew_stage"),PersistentDataType.STRING,new LegacyWortBridge().stageFor(completed.draft()));
                double purity=completed.draft().ingredientPurity().entrySet().stream()
                        .mapToDouble(e -> completed.draft().ingredientRatios().get(e.getKey())*e.getValue()).sum();
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"ingredient_purity"),PersistentDataType.INTEGER,(int)Math.round(purity));
                meta.setDisplayName(MessageConfig.title("refined-base"));
                meta.setLore(List.of("§7Stage: §bReady for cultivation§r","§7Transferred Purity: §f"+Math.round(purity)+"%§r","§7Place in a Cultivation Barrel; sneak-click it for controls.§r"));
            }
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP); me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta); item.setItemMeta(meta);
        }
        if(v2) { next[4]=null;next[5]=null; }
        else if (next[4].getAmount()==1) next[4]=null; else next[4].setAmount(next[4].getAmount()-1);
        commit(session,next,completed);
        for (var viewer:session.gui.getViewers()) viewer.sendMessage(MessageConfig.get("distillery_success"));
    }
    private void commit(Session session,ItemStack[] next,DistilleryBatchState state) {
        byte[] raw=storage.save(session.core,session.raw,next,state==null?"controls-v2:"+session.temperature+":"+session.cuts:new DistilleryProcessEnvelope(state,session.effectGeneration).encode());
        session.raw=raw; session.state=state;
        for (int slot:INPUTS) session.gui.setItem(slot,next[slot]); paint(session);
    }
    private ItemStack[] contents(Inventory inv) {
        var result=new ItemStack[9]; for (int slot:INPUTS) if (inv.getItem(slot)!=null) result[slot]=inv.getItem(slot).clone(); return result;
    }
    private void paint(Session session) {
        try { render(session); }
        catch (RuntimeException visualFailure) { plugin.getLogger().warning("Extractor display refresh failed; saved contents retained."); }
    }
    private void render(Session session) {
        String state=session.processing()?"PROCESSING":session.state!=null?"READY":Arrays.stream(contents(session.gui)).anyMatch(Objects::nonNull)?"IDLE":"EMPTY / IDLE";
        control(session.gui,3,Material.BLAZE_POWDER,"Temperature: "+session.temperature+" C",List.of("§7Click: 60–90 C in 2 C steps; ideal 78.§r","§7Slots 1–3: one bottle each.§r"));

        control(session.gui,6,Material.PAPER,"Cuts: "+List.of("Balanced","Wide / risky","Clean / mild").get(session.cuts),List.of("§7Click to choose cuts.§r","§7Wide: more potency, contamination risk.§r","§7Clean: milder potency.§r","§7Owner: §f"+access.ownerLabel(session.core)+"§r"));
        var lore=new ArrayList<String>(); lore.add("§7Status: §f"+state+"§r");
        if (session.processing()) {
            long elapsed=Math.max(0,System.currentTimeMillis()-session.state.startedAtEpochMillis());
            lore.add("§7Progress: §b"+Math.min(100,elapsed*100/session.state.durationMillis())+"%§r");
            lore.add("§7Remaining: §f"+Math.max(0,(session.state.durationMillis()-elapsed+999)/1000)+"s§r");
        }
        lore.add("§7Processing locks all machine inputs.§r");
        control(session.gui,7,session.processing()?Material.CLOCK:Material.LIME_STAINED_GLASS_PANE,state,lore);
        control(session.gui,8,Material.CRAFTING_TABLE,"Essence Extraction",List.of("§7Click to START with valid inputs.§r","§7Slots 5 and 6: 1–8 washed ingredients each.§r","§7All selected quantities consumed per run.§r","§7Stand must remain above a Crafting Table.§r"));
    }
    private void control(Inventory inv,int slot,Material type,String name,List<String> lore) {
        var item=new ItemStack(type); var meta=item.getItemMeta(); me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems.mark(meta); meta.setDisplayName("§b§l"+name+"§r"); meta.setLore(lore); me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta); item.setItemMeta(meta); inv.setItem(slot,item);
    }
    private boolean occupied(Block core) {
        var saved=storage.load(core);
        return (!saved.process().isBlank() && !saved.process().startsWith("controls-v2:") && DistilleryProcessEnvelope.decode(saved.process()).state().status()==DistilleryBatchState.Status.PROCESSING)
                || Arrays.stream(saved.items()).anyMatch(Objects::nonNull)
                || core.getState() instanceof BrewingStand stand && !stand.getInventory().isEmpty();
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType()!=Material.BREWING_STAND) return;
        try { if (occupied(event.getBlock())) { event.setCancelled(true); event.getPlayer().sendMessage(MessageConfig.prefix()+"§eEmpty the extractor before removing its core.§r"); } }
        catch (RuntimeException invalid) { event.setCancelled(true); }
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        sessions.entrySet().removeIf(entry -> entry.getValue().gui==event.getInventory() && !entry.getValue().processing());
    }
    @EventHandler public void onUnload(ChunkUnloadEvent event) {
        for (var session:List.copyOf(sessions.values())) if (session.core.getChunk().equals(event.getChunk())) detach(session);
    }
    private void stop(MachineLocation key) { var task=tasks.remove(key); if (task!=null) task.cancel(); }
    private void detach(Session session) { stop(key(session.core)); access.closeCore(session.core); sessions.remove(key(session.core),session); }
    private void unavailable(Player player,Block core) {
        plugin.getLogger().warning("Extractor data retained and locked at "+core.getLocation());
        player.sendMessage(MessageConfig.prefix()+"§cMachine data could not be updated. Stored contents were retained.§r");
    }
    public void close() { closed=true; for (var session:List.copyOf(sessions.values())) detach(session); }
}
