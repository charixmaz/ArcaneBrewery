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

/** Withdrawal-only bridge for stored items in removed producer machines. */
public final class RetiredProducerRecovery implements Listener, AutoCloseable {
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
    public RetiredProducerRecovery(JavaPlugin plugin) {
        this.plugin=plugin; storage=new MachineInventoryStorage("arcanebrewery","producer_inventory_storage",null);
        stageKey=new NamespacedKey("arcanebrewery","brew_stage"); timeKey=new NamespacedKey("arcanebrewery","slot_start_time");
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onOpen(PlayerInteractEvent event) {
        if (closed || event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.getClickedBlock()==null
                || !(event.getClickedBlock().getState() instanceof Sign sign) || !sign.getLine(0).contains("[Producer]")) return;
        event.setCancelled(true); if (event.getHand()!=EquipmentSlot.HAND) return;
        if (!event.getPlayer().hasPermission("arcanebrewery.admin.bypass")) { event.getPlayer().sendMessage(MessageConfig.prefix()+"§eThis machine is retired. Ask an administrator to recover stored items.§r"); return; }
        Block core=null;
        for (BlockFace face:BlockFace.values()) {
            Block candidate=event.getClickedBlock().getRelative(face);
            if (CaskStructure.loaded(candidate) && candidate.getType()==Material.GRAY_CONCRETE) { core=candidate; break; }
        }
        if (core==null) { event.getPlayer().sendMessage(MessageConfig.get("structure_broken")); return; }
        Block target=core;
        if (sessions.values().stream().anyMatch(session -> session.core.equals(target))) {
            event.getPlayer().sendMessage("§d[ArcaneAlchemy]§r §eThis machine is already in use.§r"); return;
        }
        try {
            var saved=storage.load(core); var holder=new Holder(); Inventory gui=Bukkit.createInventory(holder,9,"§5§lRetired Machine · Item Recovery§r"); holder.inventory=gui;
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
            if (event.getClickedInventory()!=gui) return;
            int slot=event.getSlot(); if (!Set.of(0,2,4,6).contains(slot)) return;
            ItemStack current=gui.getItem(slot); if(current==null) return;
            if(event.isShiftClick()) {
                var inventory=player.getInventory().getStorageContents();
                var delivery=ShiftTransfers.plan(current,inventory,java.util.stream.IntStream.range(0,inventory.length).toArray(),i->64,(i,item)->true);
                if(delivery.remainder()!=null) { player.sendMessage("§eMake inventory room first."); return; }
                var next=contents(gui); next[slot]=null; commit(session,next);
                player.getInventory().setStorageContents(delivery.destination());
            } else {
                var cursor=player.getItemOnCursor(); if(cursor!=null && !cursor.getType().isAir()) return;
                var next=contents(gui); next[slot]=null; commit(session,next); player.setItemOnCursor(current);
            }
        } catch(RuntimeException failure) { player.sendMessage(MessageConfig.prefix()+"§cRecovery failed; saved contents retained.§r"); detach(session); }
    }
    private ItemStack[] contents(Inventory gui) {
        var items=new ItemStack[9]; for (int slot:LANES) if (gui.getItem(slot)!=null) items[slot]=gui.getItem(slot).clone(); return items;
    }
    private void commit(Session session,ItemStack[] next) {
        session.raw=storage.save(session.core,session.raw,next,"");
        for (int slot:LANES) session.gui.setItem(slot,next[slot]); paint(session);
    }
    private void paint(Session session) {
        try {
            for(int slot:new int[]{1,3,5,7,8}) {
                var pane=new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE); var meta=pane.getItemMeta();
                meta.setDisplayName("§bWithdraw stored items§r"); meta.setLore(List.of("§7This machine has been retired.§r","§7New processing and insertion are disabled.§r"));
                pane.setItemMeta(meta); session.gui.setItem(slot,pane);
            }
        }
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
