package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientItemAdapter;
import me.arcanebrewery.arcaneBrewery.application.aging.*;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.discovery.DiscoveryRuntime;
import me.arcanebrewery.arcaneBrewery.application.economy.DrinkValueCalculator;
import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity.Kind;
import me.arcanebrewery.arcaneBrewery.application.storage.RegistrationResult;
import me.arcanebrewery.arcaneBrewery.domain.discovery.DescriptiveNameGenerator;
import me.arcanebrewery.arcaneBrewery.domain.quality.IngredientLot;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;
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
import java.time.Instant;
import java.util.*;

/** Four durable lanes, exclusive views and presentation-independent recognition. */
public final class AgingCaskEngine implements Listener, AutoCloseable {
    private static final int[] LANES={0,2,4,6};
    private final JavaPlugin plugin;
    private final IngredientItemAdapter ingredientItems;
    private final BukkitMachineAccess access;
    private final ProcessingTimes timing;
    private final FermentationBatchMetadata fermentationMetadata;
    private final AgingBatchMetadata agingMetadata;
    private final FinalDrinkMetadata finalDrinkMetadata;
    private final AlchemyPreparationMetadata preparationMetadata;
    private final DiscoveryRuntime discoveries;
    private final AlchemyEngine alchemy;
    private final MachineInventoryStorage storage;
    private final MaturationInputPolicy inputPolicy;
    private final Map<Inventory,Session> sessions=new IdentityHashMap<>();
    private final NamespacedKey slotTimeKey,recipeTypeKey;
    private boolean closed;
    private static final class Holder implements MachineGuiHolder {
        Inventory inventory; public Inventory getInventory() { return inventory; }
    }
    private static final class Session {
        final Block core; final Inventory gui; byte[] raw; BukkitRunnable task;
        Session(Block core,Inventory gui,byte[] raw) { this.core=core; this.gui=gui; this.raw=raw; }
    }
    public AgingCaskEngine(JavaPlugin plugin,IngredientItemAdapter items,DiscoveryRuntime discoveries,BukkitMachineAccess access,ProcessingTimes timing) {
        this.plugin=plugin; ingredientItems=items; this.discoveries=discoveries; this.access=access; this.timing=timing;
        fermentationMetadata=new FermentationBatchMetadata(plugin); agingMetadata=new AgingBatchMetadata(plugin);
        finalDrinkMetadata=new FinalDrinkMetadata(plugin); preparationMetadata=new AlchemyPreparationMetadata(plugin);
        alchemy=new AlchemyEngine(items.registry());
        storage=new MachineInventoryStorage(plugin.getName().toLowerCase(Locale.ROOT),"barrel_inventory_storage",null);
        inputPolicy=new MaturationInputPolicy(plugin.getName().toLowerCase(Locale.ROOT));
        slotTimeKey=new NamespacedKey(plugin,"slot_start_time"); recipeTypeKey=new NamespacedKey(plugin,"brew_stage");
        access.occupancy(Kind.CASK,core -> Arrays.stream(storage.load(core).items()).anyMatch(Objects::nonNull)
                || core.getState() instanceof Barrel barrel && !barrel.getInventory().isEmpty());
    }
    @EventHandler(ignoreCancelled=true)
    public void onSignWrite(SignChangeEvent event) {
        Block adjacent=CaskStructure.adjacentBarrel(event.getBlock());
        if (adjacent!=null && !access.permitted(event.getPlayer(),adjacent)) { event.setCancelled(true); return; }
        String tag=event.getLine(0);
        if (tag!=null && tag.equalsIgnoreCase("[Brewery]")) {
            Block core=CaskStructure.adjacentBarrel(event.getBlock());
            if (core!=null && !access.permitted(event.getPlayer(),core)) { event.setCancelled(true); return; }
            event.setLine(0,"§a§l[Brewery]§r"); event.setLine(1,"§7Maturation Cask§r"); event.setLine(2,"§7Right-click to use§r");
        } else if (tag!=null && (tag.equalsIgnoreCase("[Status]") || tag.equalsIgnoreCase("[Stats]"))) {
            event.setLine(0,"§2§l[Diagnostics]§r");
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onOpen(PlayerInteractEvent event) {
        if (closed || event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null
                || !(event.getClickedBlock().getState() instanceof Sign sign)) return;
        if (!sign.getSide(Side.FRONT).getLine(0).contains("[Brewery]")) return;
        event.setCancelled(true); if (event.getHand()!=EquipmentSlot.HAND) return;
        Block core=CaskStructure.adjacentBarrel(event.getClickedBlock());
        if (core==null || CaskStructure.wood(core).equals("UNKNOWN")) {
            event.getPlayer().sendMessage(MessageConfig.prefix()+"§eA cask needs an adjacent Barrel and at least 12 matching wood components in its 3×3×3 casing.§r"); return;
        }
        if (!access.authorize(event.getPlayer(),core,Kind.CASK,false)) return;
        if (access.identity(core).isEmpty() && core.getState() instanceof Barrel barrel && !barrel.getInventory().isEmpty()) {
            event.getPlayer().sendMessage(MessageConfig.prefix()+"§eEmpty the existing vanilla Barrel before adopting this cask.§r"); return;
        }
        try {
            var saved=storage.load(core);
            for (int slot=0;slot<9;slot++) if (!Set.of(0,2,4,6).contains(slot) && saved.items()[slot]!=null)
                throw new IllegalArgumentException("legacy cask has a bottle outside its storage lanes");
            var created=new Session[1];
            boolean opened=access.open(event.getPlayer(),core,Kind.CASK,() -> {
                var holder=new Holder(); Inventory gui=Bukkit.createInventory(holder,9,MessageConfig.title("cask")); holder.inventory=gui;
                for (int slot:LANES) gui.setItem(slot,saved.items()[slot]);
                var session=new Session(core,gui,saved.raw()); created[0]=session; sessions.put(gui,session);
                if (session.raw==null) session.raw=storage.save(core,null,contents(gui),"");
                reboot(core); paint(session); startVisuals(session); return gui;
            });
            if (!opened && created[0]!=null) detach(created[0]);
        } catch (RuntimeException failure) { unavailable(event.getPlayer(),core); }
    }
    private void reboot(Block core) {
        if (core.getState() instanceof TileState tile) {
            var key=new NamespacedKey(plugin,"cask_contaminated");
            if (tile.getPersistentDataContainer().getOrDefault(key,PersistentDataType.INTEGER,0)==2) {
                tile.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,0);
                if (!tile.update(false,false)) throw new IllegalStateException("cask reboot failed");
                CaskHygieneEngine.updateExternalSigns(plugin,core);
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        Inventory gui=event.getView().getTopInventory(); if (!(gui.getHolder() instanceof Holder)) return;
        Session session=sessions.get(gui);
        if (session==null || !(event.getWhoClicked() instanceof Player player)) { event.setCancelled(true); return; }
        if (!access.active(gui,player.getUniqueId()) || CaskStructure.wood(session.core).equals("UNKNOWN")) {
            event.setCancelled(true); return;
        }
        if (event.getClickedInventory()!=gui) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack input=event.getCurrentItem();
                if (input==null || input.getType()!=Material.POTION || !(input.getItemMeta() instanceof PotionMeta meta)
                        || meta.getPersistentDataContainer().has(new NamespacedKey(plugin,"drink_format"))) return;
                if (CaskHygieneEngine.checkInfectionStatus(plugin,session.core)) return;
                try {
                    for (int lane:LANES) if (gui.getItem(lane)==null || gui.getItem(lane).getType().isAir()) {
                        var inserted=input.clone(); inserted.setAmount(1); var nextMeta=(PotionMeta)inserted.getItemMeta();
                        if (!nextMeta.getPersistentDataContainer().has(slotTimeKey)) nextMeta.getPersistentDataContainer().set(slotTimeKey,PersistentDataType.LONG,System.currentTimeMillis());
                        begin(nextMeta,CaskStructure.wood(session.core)); inserted.setItemMeta(nextMeta);
                        var next=contents(gui); next[lane]=inserted; commit(session,next);
                        if (input.getAmount()==1) event.setCurrentItem(null);
                        else { var remaining=input.clone(); remaining.setAmount(input.getAmount()-1); event.setCurrentItem(remaining); }
                        return;
                    }
                } catch (IllegalArgumentException invalid) {
                    player.sendMessage(MessageConfig.prefix()+"§eA canonical base must finish cultivation before maturation. Input retained.§r");
                } catch (RuntimeException invalid) { unavailable(player,session.core); detach(session); }
            }
            return;
        }
        event.setCancelled(true); int slot=event.getSlot();
        if (slot==8) { clean(player,session); return; }
        if (!Set.of(0,2,4,6).contains(slot)) return;
        if (event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT && !event.isShiftClick()) return;
        if (CaskHygieneEngine.checkInfectionStatus(plugin,session.core)) {
            player.sendMessage(MessageConfig.prefix()+"§eCONTAMINATED: apply Cleansing Solution to the cleaning control.§r"); return;
        }
        ItemStack existing=gui.getItem(slot),cursor=player.getItemOnCursor();
        try {
            if (existing!=null && !existing.getType().isAir()) {
                if (cursor!=null && !cursor.getType().isAir()) { player.sendMessage(MessageConfig.prefix()+"§eEmpty your cursor before withdrawing a lane.§r"); return; }
                withdraw(player,session,slot,event.isShiftClick()); return;
            }
            if (cursor==null || cursor.getType().isAir()) return;
            if (cursor.getType()!=Material.POTION || !(cursor.getItemMeta() instanceof PotionMeta meta)
                    || meta.getPersistentDataContainer().has(new NamespacedKey(plugin,"drink_format"))) {
                player.sendMessage(MessageConfig.prefix()+"§eInsert a cultivated base or a compatible legacy base.§r"); return;
            }
            var move=AlchemySlotTransfer.move(null,cursor,event.getClick()==ClickType.RIGHT,1);
            ItemStack inserted=move.slot();
            if (inserted==null) return;
            PotionMeta nextMeta=(PotionMeta)inserted.getItemMeta();
            if (!nextMeta.getPersistentDataContainer().has(slotTimeKey)) nextMeta.getPersistentDataContainer().set(slotTimeKey,PersistentDataType.LONG,System.currentTimeMillis());
            begin(nextMeta,CaskStructure.wood(session.core)); inserted.setItemMeta(nextMeta);
            var next=contents(gui); next[slot]=inserted; commit(session,next); player.setItemOnCursor(move.cursor());
        } catch (IllegalArgumentException invalid) {
            player.sendMessage(MessageConfig.prefix()+"§eThis input cannot mature with its current metadata. Contents were retained.§r");
        } catch (RuntimeException corrupt) { unavailable(player,session.core); detach(session); }
    }
    private void begin(PotionMeta meta,String wood) {
        var fermented=inputPolicy.completedSource(meta,System.currentTimeMillis());
        if (fermented.isEmpty()) return; // Preserve legacy contract without inventing canonical recipes.
        if (fermented.get().status()!=FermentationBatchState.Status.COMPLETED) throw new IllegalArgumentException("cultivation is incomplete");
        preparationMetadata.read(meta); // Malformed additives never silently become absent.
        if (agingMetadata.read(meta).isEmpty()) agingMetadata.write(meta,new AgingBatchState(AgingBatchState.VERSION,UUID.randomUUID(),

                new AgingSettings(wood,fermented.get().distillery().preparationMethod().equals("distillation-v2")?0:12,18,fermented.get().distillery().preparationMethod().equals("distillation-v2")?100:95),System.currentTimeMillis(),timing.maturationSeconds()));
    }
    private void withdraw(Player player,Session session,int slot,boolean shift) {
        ItemStack source=session.gui.getItem(slot);
        if (source.getAmount()!=1) { player.sendMessage(MessageConfig.prefix()+"§eThis legacy stacked lane is retained for administrator inspection.§r"); return; }
        PotionMeta meta=source.getItemMeta() instanceof PotionMeta potion?potion:null;
        ItemStack output=source.clone(); AgingResult result=null; boolean mature=false; int legacyStars=-1;
        byte[] privateEvidence=null;String productionKey=null;
        if (meta!=null) {
            var fermentation=fermentationMetadata.read(meta); var aging=agingMetadata.read(meta);
            if (fermentation.isPresent() || aging.isPresent()) {
                if (fermentation.isEmpty() || aging.isEmpty() || fermentation.get().status()!=FermentationBatchState.Status.COMPLETED
                        || !aging.get().settings().woodType().equalsIgnoreCase(CaskStructure.wood(session.core)))
                    throw new IllegalArgumentException("canonical cask metadata does not match");
                var state=aging.get(); long now=System.currentTimeMillis();
                mature=state.elapsedSeconds(now)>=state.maturationSeconds();
                var lots=new TreeMap<String,IngredientLot>();
                fermentation.get().distillery().ingredientPurity().forEach((id,purity) -> lots.put(id,new IngredientLot(ingredientItems.registry().require(id).profile(),purity)));
                var additives=preparationMetadata.read(meta).map(p -> p.additives()).orElseGet(Map::of);
                var alchemyConditions=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyBatchConditionsMetadata(plugin.getName().toLowerCase(Locale.ROOT));
                result=new AgingService().finish(fermentation.get().fermentedDraft(),state,now,lots,AgingBalance.wood(CaskStructure.wood(session.core)),
                        Math.min(100,Math.max(0,100-state.settings().caskCleanliness())+alchemyConditions.contamination(meta)),AgingBalance.targets(),state.batchId().getMostSignificantBits()^state.batchId().getLeastSignificantBits(),additives,alchemyConditions.stability(meta));

                output=bottle(result,mature,player);
                privateEvidence=source.serializeAsBytes();
                if(privateEvidence.length>1048576) throw new IllegalArgumentException("batch evidence exceeds storage bound");
                productionKey=state.batchId().toString();
            } else if (inputPolicy.legacyEligible(meta) && meta.getPersistentDataContainer().has(slotTimeKey,PersistentDataType.LONG)) {
                String stage=meta.getPersistentDataContainer().getOrDefault(recipeTypeKey,PersistentDataType.STRING,"");
                if (BreweryData.RECIPES.containsKey(stage)) {
                    legacyStars=CaskQualityEngine.calculateHarvestStars(plugin,source,CaskStructure.wood(session.core),session.core);
                    output=legacyBottle(legacyStars);
                }
            }
        }
        ItemStack[] next=contents(session.gui); next[slot]=null; commit(session,next);
        if (shift) give(player,output); else player.setItemOnCursor(output);
        if (legacyStars>=1) CaskQualityEngine.incrementBatchCounter(plugin,session.core);
        player.sendMessage(MessageConfig.prefix()+(mature?"§aElixir matured and bottled.§r":"§eWithdrawn before full maturity; no recipe discovery is registered.§r"));
        if (mature && result!=null) registerDiscovery(player.getUniqueId(),result,productionKey,privateEvidence);

        if(mature && result!=null && result.assessment()!=null) result.assessment().breakdown().forEach(line->player.sendMessage(MessageConfig.prefix()+"§7"+line+"§r"));
    }

    private ItemStack bottle(AgingResult result,boolean mature,Player producer) {
        var item=new ItemStack(Material.POTION); var meta=(PotionMeta)item.getItemMeta();
        if (meta==null) throw new IllegalStateException("potion meta unavailable");
        var effect=alchemy.evaluate(result.process(),result.simulation().quality());
        var value=new DrinkValueCalculator().calculate(result.process(),result.simulation().quality(),effect);
        finalDrinkMetadata.write(meta,result,effect,value);

        if(mature && result.process().preparationMethod().equals("distillation-v2")) finalDrinkMetadata.producer(meta,producer.getUniqueId(),producer.getName(),result.process());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"alcohol_stars"),PersistentDataType.INTEGER,qualityStars(result.simulation().quality().overallScore()));
        String name=mature?new DescriptiveNameGenerator().generate(result.process()).value():MessageConfig.text("names.unfinished");
        meta.setDisplayName("§d§l"+name+"§r"); ElixirPresentation.refresh(meta,finalDrinkMetadata); item.setItemMeta(meta); return item;
    }
    private ItemStack legacyBottle(int stars) {
        var item=new ItemStack(Material.POTION); var meta=(PotionMeta)item.getItemMeta();
        meta.setDisplayName("§d§lLegacy Elixir§r"); meta.setLore(List.of("§7Legacy Quality: §f"+stars+" stars§r","§7Compatibility batch; no canonical recipe discovery.§r"));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"alcohol_stars"),PersistentDataType.INTEGER,stars);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP); PresentationStyle.apply(meta); item.setItemMeta(meta); return item;
    }
    private int qualityStars(double value) { return value<20?0:value<40?1:value<55?2:value<70?3:value<82?4:value<94?5:6; }
    private void give(Player player,ItemStack item) { player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(),left)); }
    private void clean(Player player,Session session) {
        ItemStack cursor=player.getItemOnCursor();
        if (cursor==null || !(cursor.getItemMeta() instanceof PotionMeta meta)
                || !"INDUSTRIAL_METHANOL".equals(meta.getPersistentDataContainer().get(recipeTypeKey,PersistentDataType.STRING))) {
            player.sendMessage(MessageConfig.prefix()+"§eApply a Cleansing Solution from your cursor to this control.§r"); return;
        }
        if (!CaskHygieneEngine.checkInfectionStatus(plugin,session.core)) { player.sendMessage(MessageConfig.prefix()+"§aThe cask is already clean.§r"); return; }
        if (!(session.core.getState() instanceof TileState tile)) throw new IllegalStateException("cask tile unavailable");
        tile.getPersistentDataContainer().set(new NamespacedKey(plugin,"cask_contaminated"),PersistentDataType.INTEGER,2);
        tile.getPersistentDataContainer().remove(new NamespacedKey(plugin,"cask_spoil_timestamp"));
        if (!tile.update(false,false)) throw new IllegalStateException("cleaning could not be persisted");
        if (cursor.getAmount()==1) player.setItemOnCursor(new ItemStack(Material.GLASS_BOTTLE));
        else { var remaining=cursor.clone(); remaining.setAmount(remaining.getAmount()-1); player.setItemOnCursor(remaining); give(player,new ItemStack(Material.GLASS_BOTTLE)); }
        Block top=session.core.getRelative(0,1,0); if (top.getType()==Material.BROWN_MUSHROOM) top.setType(Material.AIR);
        player.sendMessage(MessageConfig.prefix()+"§aCleansing Solution applied. Close this view, then use the [Brewery] sign to restart ventilation.§r"); paint(session);
    }
    private ItemStack[] contents(Inventory gui) {
        var items=new ItemStack[9]; for (int slot:LANES) if (gui.getItem(slot)!=null) items[slot]=gui.getItem(slot).clone(); return items;
    }
    private void commit(Session session,ItemStack[] next) {
        session.raw=storage.save(session.core,session.raw,next,"");
        for (int slot:LANES) session.gui.setItem(slot,next[slot]); paint(session);
    }
    private void paint(Session session) {
        try { CaskVisualEngine.updateCaskInterface(plugin,session.gui,session.core,access.ownerLabel(session.core)); }
        catch (RuntimeException visualFailure) { plugin.getLogger().warning("Cask display refresh failed; saved contents retained."); }
    }
    private void startVisuals(Session session) {
        session.task=new BukkitRunnable() { public void run() {
            if (closed || !CaskStructure.loaded(session.core) || CaskStructure.wood(session.core).equals("UNKNOWN")) { detach(session); return; }
            paint(session);
        }}; session.task.runTaskTimer(plugin,20,20);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        var session=sessions.remove(event.getInventory()); if (session!=null && session.task!=null) session.task.cancel();
    }
    @EventHandler public void onUnload(ChunkUnloadEvent event) {
        for (var session:List.copyOf(sessions.values())) if (session.core.getChunk().equals(event.getChunk())) detach(session);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType()!=Material.BARREL) return;
        try { if (Arrays.stream(storage.load(event.getBlock()).items()).anyMatch(Objects::nonNull)) event.setCancelled(true); }
        catch (RuntimeException corrupt) { event.setCancelled(true); }
    }
    private void detach(Session session) { if (session.task!=null) session.task.cancel(); access.closeCore(session.core); sessions.remove(session.gui); }
    private void unavailable(Player player,Block core) {
        plugin.getLogger().warning("Cask data retained and locked at "+core.getLocation());
        player.sendMessage(MessageConfig.prefix()+"§cCask data could not be updated. Contents were retained.§r");
    }
    private void registerDiscovery(UUID playerId,AgingResult result,String batchKey,byte[] evidence) {

        discoveries.register(playerId,result.process(),Instant.now(),result.simulation().quality().overallScore(),batchKey,evidence).whenComplete((registration,failure) -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin,() -> {
                Player player=Bukkit.getPlayer(playerId); if (player==null) return;

                if (failure!=null) { player.sendMessage(MessageConfig.prefix()+"§cElixir bottled, but personal discovery could not be saved.§r"); return; }
                player.sendMessage(MessageConfig.prefix()+(registration.status()==RegistrationResult.Status.REGISTERED

                        ?"§aYou discovered a new recipe! Private personal record unlocked.§r":"§7Recipe reproduced; personal record retained.§r"));
            });
        });
    }
    public void close() { closed=true; for (var session:List.copyOf(sessions.values())) detach(session); }
}
