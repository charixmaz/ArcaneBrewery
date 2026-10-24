package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemySlotTransfer;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

/** Legacy cleansing producer compatibility, with unchanged inputs and 10s timing.
 * It does not introduce a fifth ownership kind or new cleaning mechanics. */
public final class MethanolProducerEngine implements Listener, AutoCloseable {
    private static final int[] LANES={0,2,4,6};
    private final JavaPlugin plugin;
    private final MachineInventoryStorage storage;
    private final Map<Inventory,Session> sessions=new IdentityHashMap<>();
    private final NamespacedKey stageKey,timeKey;
    private boolean closed;
    private static final class Holder implements MachineAutomationHolder {
        Inventory inventory; public Inventory getInventory() { return inventory; }
    }
    private static final class Session {
        final Block core; final Inventory gui; byte[] raw; BukkitRunnable task;
        Session(Block core,Inventory gui,byte[] raw) { this.core=core; this.gui=gui; this.raw=raw; }
    }
    public MethanolProducerEngine(JavaPlugin plugin) {
        this.plugin=plugin; storage=new MachineInventoryStorage(plugin.getName().toLowerCase(Locale.ROOT),"producer_inventory_storage",null);
        stageKey=new NamespacedKey(plugin,"brew_stage"); timeKey=new NamespacedKey(plugin,"slot_start_time");
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onOpen(PlayerInteractEvent event) {
        if (closed || event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null
                || !(event.getClickedBlock().getState() instanceof Sign sign) || !sign.getLine(0).contains("[Producer]")) return;
        event.setCancelled(true); if (event.getHand()!=EquipmentSlot.HAND) return;
        Block core=null;
        for (BlockFace face:BlockFace.values()) {
            Block candidate=event.getClickedBlock().getRelative(face);
            if (CaskStructure.loaded(candidate) && candidate.getType()==Material.GRAY_CONCRETE) { core=candidate; break; }
        }
        if (core==null) { event.getPlayer().sendMessage(MessageConfig.get("structure_broken")); return; }
        Block target=core;
        if (sessions.values().stream().anyMatch(session -> session.core.equals(target))) {
            event.getPlayer().sendMessage("§d[ArcaneBrewery]§r §eThis machine is already in use.§r"); return;
        }
        try {
            var saved=storage.load(core); var holder=new Holder(); Inventory gui=Bukkit.createInventory(holder,9,"§5§lCleansing Solution Producer§r"); holder.inventory=gui;
            for (int slot=0;slot<9;slot++) {
                if (!Set.of(0,2,4,6).contains(slot) && saved.items()[slot]!=null) throw new IllegalArgumentException("legacy control slot contains an item");
                if (Set.of(0,2,4,6).contains(slot)) gui.setItem(slot,saved.items()[slot]);
            }
            var session=new Session(core,gui,saved.raw());
            if (session.raw==null) session.raw=storage.save(core,null,contents(gui),"");
            sessions.put(gui,session); paint(session); event.getPlayer().openInventory(gui);
            if (event.getPlayer().getOpenInventory().getTopInventory()!=gui) { detach(session); return; }
            session.task=new BukkitRunnable() { public void run() {
                if (!CaskStructure.loaded(session.core) || session.core.getType()!=Material.GRAY_CONCRETE) { detach(session); return; }
                paint(session);
            }}; session.task.runTaskTimer(plugin,20,20);
        } catch (RuntimeException failure) { event.getPlayer().sendMessage(MessageConfig.prefix()+"§cProducer data is unavailable; stored contents were retained.§r"); }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        Inventory gui=event.getView().getTopInventory(); if (!(gui.getHolder() instanceof Holder)) return;
        Session session=sessions.get(gui);
        if (session==null || !(event.getWhoClicked() instanceof Player player)) { event.setCancelled(true); return; }
        if (event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT && !event.isShiftClick()) { event.setCancelled(true); return; }
        if (event instanceof InventoryCreativeEvent) { event.setCancelled(true); return; }
        if (event.getClickedInventory()!=gui && !event.isShiftClick()) return;
        event.setCancelled(true);
        try {
            if (event.getClickedInventory()!=gui) {
                ItemStack input=event.getCurrentItem(); if (!valid(input)) { player.sendMessage(MessageConfig.get("producer_reject")); return; }
                for (int slot:LANES) if (gui.getItem(slot)==null) {
                    var inserted=input.clone(); inserted.setAmount(1); stamp(inserted);
                    var next=contents(gui); next[slot]=inserted; commit(session,next);
                    if (input.getAmount()==1) event.setCurrentItem(null);
                    else { var left=input.clone(); left.setAmount(left.getAmount()-1); event.setCurrentItem(left); }
                    return;
                }
                return;
            }
            int slot=event.getSlot(); if (!Set.of(0,2,4,6).contains(slot)) return;
            ItemStack current=gui.getItem(slot);
            if (event.isShiftClick()) {
                if (current==null || current.getAmount()!=1 || !(current.getItemMeta() instanceof PotionMeta meta)) return;
                long started=meta.getPersistentDataContainer().getOrDefault(timeKey,PersistentDataType.LONG,System.currentTimeMillis());
                if (System.currentTimeMillis()-started<10_000) { player.sendMessage(MessageConfig.get("producer_incomplete")); return; }
                if (!valid(current)) { player.sendMessage(MessageConfig.get("producer_reject")); return; }
                var output=new ItemStack(Material.POTION); var outputMeta=(PotionMeta)output.getItemMeta();
                outputMeta.setDisplayName(MessageConfig.title("cleanser"));
                outputMeta.setLore(List.of("§7For the existing cask cleaning control.§r","§cUnsafe solution: do not consume.§r"));
                outputMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(outputMeta);
                outputMeta.getPersistentDataContainer().set(stageKey,PersistentDataType.STRING,"INDUSTRIAL_METHANOL"); output.setItemMeta(outputMeta);
                var next=contents(gui); next[slot]=null; commit(session,next);
                player.getInventory().addItem(output).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(),left));
                player.sendMessage(MessageConfig.get("producer_success")); return;
            }
            ItemStack cursor=player.getItemOnCursor();
            if (cursor!=null && cursor.getType()!=Material.AIR && !valid(cursor)) { player.sendMessage(MessageConfig.get("producer_reject")); return; }
            var move=AlchemySlotTransfer.move(current,cursor,event.getClick()==ClickType.RIGHT,1);
            if (move.slot()!=null && valid(move.slot())) stamp(move.slot());
            var next=contents(gui); next[slot]=move.slot(); commit(session,next); player.setItemOnCursor(move.cursor());
        } catch (RuntimeException failure) { player.sendMessage(MessageConfig.prefix()+"§cProducer transfer failed; saved contents were retained.§r"); detach(session); }
    }
    private boolean valid(ItemStack item) {
        return item!=null && item.getType()==Material.POTION && item.getItemMeta() instanceof PotionMeta meta
                && "REFINED_INDUSTRIAL_ALCOHOL".equals(meta.getPersistentDataContainer().get(stageKey,PersistentDataType.STRING));
    }
    private void stamp(ItemStack item) {
        var meta=item.getItemMeta(); if (!meta.getPersistentDataContainer().has(timeKey)) meta.getPersistentDataContainer().set(timeKey,PersistentDataType.LONG,System.currentTimeMillis()); item.setItemMeta(meta);
    }
    private ItemStack[] contents(Inventory gui) {
        var items=new ItemStack[9]; for (int slot:LANES) if (gui.getItem(slot)!=null) items[slot]=gui.getItem(slot).clone(); return items;
    }
    private void commit(Session session,ItemStack[] next) {
        session.raw=storage.save(session.core,session.raw,next,"");
        for (int slot:LANES) session.gui.setItem(slot,next[slot]); paint(session);
    }
    private void paint(Session session) {
        try { ProducerVisualEngine.updateProducerInterface(plugin,session.gui,session.core); }
        catch (RuntimeException failure) { plugin.getLogger().warning("Producer display refresh failed; contents retained."); }
    }
    @EventHandler(ignoreCancelled=true) public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder && event.getRawSlots().stream().anyMatch(slot -> slot<9)) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) { var session=sessions.get(event.getInventory()); if (session!=null) detach(session); }
    @EventHandler public void onUnload(ChunkUnloadEvent event) {
        for (var session:List.copyOf(sessions.values())) if (session.core.getChunk().equals(event.getChunk())) detach(session);
    }
    private boolean occupied(Block core) {
        try { return Arrays.stream(storage.load(core).items()).anyMatch(Objects::nonNull); }
        catch (RuntimeException corrupt) { return true; }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType()==Material.GRAY_CONCRETE && occupied(event.getBlock())) event.setCancelled(true);
    }
    private void protect(List<Block> blocks) { blocks.removeIf(block -> block.getType()==Material.GRAY_CONCRETE && occupied(block)); }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void onExplosion(EntityExplodeEvent event) { protect(event.blockList()); }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void onExplosion(BlockExplodeEvent event) { protect(event.blockList()); }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void onPiston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType()==Material.GRAY_CONCRETE && occupied(block))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void onPiston(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> block.getType()==Material.GRAY_CONCRETE && occupied(block))) event.setCancelled(true);
    }
    private void detach(Session session) {
        if (sessions.remove(session.gui)==null) return;
        if (session.task!=null) session.task.cancel();
        for (var viewer:List.copyOf(session.gui.getViewers())) viewer.closeInventory();
    }
    public void close() { closed=true; for (var session:List.copyOf(sessions.values())) detach(session); }
}
