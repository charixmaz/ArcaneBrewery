package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.*;
import me.arcanebrewery.arcaneBrewery.application.machine.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Shared server-thread access boundary. Ownership is chunk-persistent; leases
 * are temporary and tied to the actual inventory, never a display title. */
public final class BukkitMachineAccess implements Listener, AutoCloseable {
    public static final String BYPASS = "arcanebrewery.admin.bypass";
    private final Plugin plugin;
    private final MachineIdentityStorage storage;
    private final MachineSessions leases = new MachineSessions();
    private record View(Block core, MachineSessions.Lease lease) { }
    private final Map<Inventory,View> views = new IdentityHashMap<>();
    private final Set<Inventory> custom = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Inventory,MachineIdentity> pendingClaims=new IdentityHashMap<>();
    private final Map<MachineIdentity.Kind,Predicate<Block>> occupied = new EnumMap<>(MachineIdentity.Kind.class);
    private boolean closed;
    public BukkitMachineAccess(Plugin plugin) {
        this.plugin = plugin; storage = new MachineIdentityStorage(plugin.getName().toLowerCase(Locale.ROOT));
    }
    public void occupancy(MachineIdentity.Kind kind, Predicate<Block> check) { occupied.put(kind,check); }
    public Optional<MachineIdentity> identity(Block core) { return storage.read(core); }
    public boolean permitted(Player player, Block core) {
        try { return identity(core).map(id -> id.permits(player.getUniqueId(),player.hasPermission(BYPASS))).orElse(true); }
        catch (RuntimeException corrupt) { return false; }
    }
    public boolean authorize(Player player,Block core,MachineIdentity.Kind kind,boolean claim) {
        if (closed) return false;
        try {
            var existing = identity(core);
            if (existing.isPresent() && existing.get().kind()!=kind) {
                player.sendMessage(MessageConfig.prefix()+"§eThis core is already a different machine. Empty and remove it before converting it.§r"); return false;
            }
            if (existing.isPresent() && !permitted(player,core)) {
                player.sendMessage(MessageConfig.prefix() + "§cThis machine belongs to §f" + existing.get().displayOwner() + "§c.§r"); return false;
            }
            if (existing.isEmpty() && claim) storage.write(core,new MachineIdentity(UUID.randomUUID(),kind,player.getUniqueId(),player.getName()));
            return true;
        } catch (RuntimeException invalid) {
            player.sendMessage(MessageConfig.prefix() + "§cMachine ownership data is unavailable; access is locked.§r"); return false;
        }
    }
    public boolean open(Player player,Block core,MachineIdentity.Kind kind,Supplier<Inventory> create) {
        boolean unclaimed=identity(core).isEmpty();
        if (!authorize(player,core,kind,true)) return false;
        var identity = identity(core).orElseThrow();
        var lease = leases.acquire(identity.machineId(),player.getUniqueId());
        if (lease.isEmpty()) { busy(player); return false; }
        Inventory inventory = null;
        try {
            inventory = create.get(); views.put(inventory,new View(core,lease.get())); custom.add(inventory);
            player.openInventory(inventory);
            // Another protection plugin may cancel InventoryOpenEvent.
            if (player.getOpenInventory().getTopInventory() != inventory) {
                release(inventory); if (unclaimed) rollbackClaim(core,identity); return false;
            }
            return true;
        } catch (RuntimeException failure) {
            leases.release(lease.get()); if (inventory != null) { views.remove(inventory); custom.remove(inventory); }
            if (unclaimed) rollbackClaim(core,identity);
            throw failure;
        }
    }
    public boolean active(Inventory inv,UUID player) {
        var view = views.get(inv);
        return view != null && leases.owns(view.lease(),player);
    }
    public String ownerLabel(Block core) { return identity(core).map(MachineIdentity::displayOwner).orElse("Unclaimed"); }
    public void release(Inventory inventory) {
        var view = views.remove(inventory); custom.remove(inventory);
        if (view != null) leases.release(view.lease());
    }
    private void busy(Player player) { player.sendMessage("§d[ArcaneBrewery]§r §eThis machine is already in use.§r"); }

    public boolean canonicalBase(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || !item.hasItemMeta()) return false;
        try {
            var meta=item.getItemMeta();
            String namespace=plugin.getName().toLowerCase(Locale.ROOT);
            var fermentation=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata(namespace).read(meta);
            if (fermentation.isPresent()) return true;
            var distilled=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery.DistilleryBatchMetadata(namespace).read(meta);
            return distilled.isPresent() && distilled.get().status()==me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryBatchState.Status.COMPLETED;
        } catch (RuntimeException malformed) { return false; }
    }
    public MachineIdentity.Kind recognized(Block core) {
        if (!CaskStructure.loaded(core)) return null;
        var stored = identity(core); if (stored.isPresent()) return stored.get().kind();
        if (hasLegacyData(core)) return switch(core.getType()) {
            case BREWING_STAND -> MachineIdentity.Kind.EXTRACTOR;
            case ENCHANTING_TABLE -> MachineIdentity.Kind.INFUSER;
            case BARREL -> MachineIdentity.Kind.CASK;
            default -> null;
        };
        if (core.getType() == Material.BREWING_STAND && DistilleryStructureScanner.isValidDistillery(core)) return MachineIdentity.Kind.EXTRACTOR;
        if (core.getType() == Material.ENCHANTING_TABLE && AlchemyStructureScanner.isValid(core)) return MachineIdentity.Kind.INFUSER;
        if (core.getType() == Material.BARREL) {
            if (CaskStructure.hasSign(core) && !CaskStructure.wood(core).equals("UNKNOWN")) return MachineIdentity.Kind.CASK;
            if (core.getState() instanceof Barrel barrel && Arrays.stream(barrel.getInventory().getContents()).anyMatch(this::canonicalBase)) return MachineIdentity.Kind.CULTIVATION;
        }
        return null;
    }
    private boolean hasLegacyData(Block core) {
        if (core.getType()!=Material.BREWING_STAND && core.getType()!=Material.BARREL && core.getType()!=Material.ENCHANTING_TABLE) return false;
        String ns=plugin.getName().toLowerCase(Locale.ROOT);
        var inventories=new MachineInventoryStorage(ns,"barrel_inventory_storage",null);
        var chunk=core.getChunk().getPersistentDataContainer();
        if (chunk.has(inventories.key(core))) {
            try {
                byte[] raw=chunk.get(inventories.key(core),PersistentDataType.BYTE_ARRAY);
                if (raw==null) return true;
                var snapshot=new MachineInventoryCodec().decode(raw);
                if (!snapshot.process().isBlank() || snapshot.slots().stream().anyMatch(item -> item.length>0)) return true;
                // An empty, unowned record left by an old non-tile producer is
                // not authority to turn a replacement vanilla block into a machine.
            } catch (RuntimeException corrupt) { return true; }
        }
        if (core.getState() instanceof PersistentDataHolder holder) {
            var data=holder.getPersistentDataContainer();
            for (String key:List.of("distillery_inventory_storage","distillery_batch_state","barrel_inventory_storage","alchemy_machine_snapshot","alchemy_machine_inventory"))
                if (data.has(new NamespacedKey(ns,key))) return true;
        }
        return false;
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onInteract(PlayerInteractEvent event) {
        Block clicked = event.getClickedBlock(); if (clicked == null) return;
        Block core = clicked;
        if (clicked.getType().name().contains("SIGN")) {
            Block adjacent = CaskStructure.adjacentBarrel(clicked); if (adjacent != null) core = adjacent;
        } else if (clicked.getType() == Material.CRAFTING_TABLE && clicked.getRelative(0,1,0).getType() == Material.BREWING_STAND) core = clicked.getRelative(0,1,0);
        if (!permitted(event.getPlayer(),core)) {
            event.setCancelled(true);
            String owner;
            try { owner=ownerLabel(core); } catch (RuntimeException corrupt) { owner="Unavailable ownership record"; }
            event.getPlayer().sendMessage(MessageConfig.prefix() + "§cThis machine belongs to §f"+owner+"§c.§r");
            return;
        }
        Block actual=core;
        if (views.values().stream().anyMatch(view -> view.core().equals(actual))) {
            event.setCancelled(true);
            if (event.getHand()==EquipmentSlot.HAND) busy(event.getPlayer());
        }
    }
    private Block block(Inventory inv) {
        var customView = views.get(inv); if (customView != null) return customView.core();
        return inv.getHolder() instanceof BlockState state ? state.getBlock() : null;
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onOpen(InventoryOpenEvent event) {
        if (custom.contains(event.getInventory()) || !(event.getPlayer() instanceof Player player)) return;
        Block core = block(event.getInventory()); if (core == null) return;
        try {
            var kind = recognized(core);
            boolean existingNativeCask=kind==MachineIdentity.Kind.CASK && identity(core).isEmpty() && !event.getInventory().isEmpty();
            if ((kind == MachineIdentity.Kind.CASK && !existingNativeCask) || (kind == MachineIdentity.Kind.EXTRACTOR && identity(core).isPresent())) {
                event.setCancelled(true); player.sendMessage(MessageConfig.prefix() + "§eUse the machine interface to access its contents.§r"); return;
            }
            if (kind != MachineIdentity.Kind.CULTIVATION) return;
            boolean unclaimed=identity(core).isEmpty();
            if (!authorize(player,core,kind,true) || !attachBarrel(player,core,event.getInventory())) event.setCancelled(true);
            else if (unclaimed) pendingClaims.put(event.getInventory(),identity(core).orElseThrow());
        } catch (RuntimeException invalid) { event.setCancelled(true); }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void afterOpen(InventoryOpenEvent event) {
        var view=views.get(event.getInventory());
        if (view==null || !view.lease().playerId().equals(event.getPlayer().getUniqueId())) return;
        var provisional=pendingClaims.remove(event.getInventory());
        if (event.isCancelled()) {
            release(event.getInventory());
            if (provisional!=null) rollbackClaim(view.core(),provisional);
        }
    }
    private void rollbackClaim(Block core,MachineIdentity provisional) {
        try { if (identity(core).filter(provisional::equals).isPresent()) storage.remove(core); }
        catch (RuntimeException unavailable) { plugin.getLogger().warning("Provisional machine ownership retained until its chunk is available."); }
    }
    private boolean attachBarrel(Player player,Block core,Inventory inv) {
        if (active(inv,player.getUniqueId())) return true;
        var lease = leases.acquire(identity(core).orElseThrow().machineId(),player.getUniqueId());
        if (lease.isEmpty()) { busy(player); return false; }
        views.put(inv,new View(core,lease.get()));
        for (var viewer : List.copyOf(inv.getViewers())) if (!viewer.getUniqueId().equals(player.getUniqueId())) viewer.closeInventory();
        return true;
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onClick(InventoryClickEvent event) {
        Inventory inv = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var view = views.get(inv);
        if (view==null && inv.getHolder() instanceof MachineGuiHolder) { event.setCancelled(true); return; }
        if (view == null && inv.getHolder() instanceof Barrel barrel) {
            ItemStack candidate = event.getCursor();
            if (event.isShiftClick() && event.getClickedInventory() != inv) candidate = event.getCurrentItem();
            if (event.getHotbarButton() >= 0) candidate = player.getInventory().getItem(event.getHotbarButton());
            if (event.getClick()==ClickType.SWAP_OFFHAND) candidate=player.getInventory().getItemInOffHand();
            if (canonicalBase(candidate)) {
                if (recognized(barrel.getBlock())==MachineIdentity.Kind.CASK) { event.setCancelled(true); return; }
                if (!authorize(player,barrel.getBlock(),MachineIdentity.Kind.CULTIVATION,true) || !attachBarrel(player,barrel.getBlock(),inv)) {
                    event.setCancelled(true); return;
                }
                view = views.get(inv);
            }
        }
        if (view == null) return;
        if (!active(inv,player.getUniqueId()) || !permitted(player,view.core())) { event.setCancelled(true); return; }
        if (custom.contains(inv)) {
            boolean simple = event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT;
            boolean caskHarvest = event.isShiftClick();
            if ((!simple && !caskHarvest) || event instanceof InventoryCreativeEvent) event.setCancelled(true);
        }
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onDrag(InventoryDragEvent event) {
        Inventory inv = event.getView().getTopInventory();
        if ((custom.contains(inv) || inv.getHolder() instanceof MachineGuiHolder) && event.getRawSlots().stream().anyMatch(slot -> slot < inv.getSize())) event.setCancelled(true);
        else if (views.containsKey(inv) && !active(inv,event.getWhoClicked().getUniqueId())) event.setCancelled(true);
        else if (inv.getHolder() instanceof Barrel && canonicalBase(event.getOldCursor())
                && event.getRawSlots().stream().anyMatch(slot -> slot < inv.getSize())) event.setCancelled(true); // Claim via a normal click first.
    }
    private boolean automated(Inventory inv) {
        if (inv.getHolder() instanceof MachineAutomationHolder) return true;
        if (views.containsKey(inv)) return true;
        Block core = block(inv); if (core == null) return false;
        try { return recognized(core) != null; } catch (RuntimeException corrupt) { return true; }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onMove(InventoryMoveItemEvent event) {
        if (automated(event.getSource()) || automated(event.getDestination())
                || (event.getDestination().getHolder() instanceof Barrel && canonicalBase(event.getItem()))) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onPickup(InventoryPickupItemEvent event) { if (automated(event.getInventory())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onBrew(BrewEvent event) {
        try { if (recognized(event.getBlock())==MachineIdentity.Kind.EXTRACTOR) event.setCancelled(true); }
        catch (RuntimeException corrupt) { event.setCancelled(true); }
    }
    private List<Block> claimsNear(Block changed) {
        var result = new ArrayList<Block>();
        for (int x=-2;x<=2;x++) for (int y=-2;y<=2;y++) for (int z=-2;z<=2;z++) {
            Block core = changed.getRelative(x,y,z); if (!CaskStructure.loaded(core)) continue;
            try {
                var identity = identity(core);
                if (identity.isPresent() && component(identity.get().kind(),core,changed)) result.add(core);
                else if (identity.isEmpty() && hasLegacyData(core) && component(recognized(core),core,changed)) result.add(core);
            } catch (RuntimeException corrupt) { result.add(core); }
        }
        return result;
    }
    private boolean component(MachineIdentity.Kind kind,Block core,Block changed) {
        int x=changed.getX()-core.getX(),y=changed.getY()-core.getY(),z=changed.getZ()-core.getZ();
        return switch (kind) {
            case SPECIALIST -> x==0 && z==0 && (y==0 || y==-1);
            case CULTIVATION -> x==0 && y==0 && z==0;
            case EXTRACTOR -> x==0 && z==0 && (y==0 || y==-1);
            case INFUSER -> (y==0 && Math.abs(x)+Math.abs(z)<=1) || (y==-1 && x==0 && z==0);
            case CASK -> (Math.abs(x)<=1 && Math.abs(y)<=1 && Math.abs(z)<=1)
                    || (changed.getState() instanceof Sign && core.equals(CaskStructure.adjacentBarrel(changed)));
        };
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event) {
        for (Block core : claimsNear(event.getBlock())) {
            try {
                var identity = identity(core).orElseThrow();
                boolean full = core.equals(event.getBlock()) && occupied.getOrDefault(identity.kind(),b ->
                        b.getState() instanceof Container container && !container.getInventory().isEmpty()).test(core);
                if (!permitted(event.getPlayer(),core) || full) {
                    event.setCancelled(true); event.getPlayer().sendMessage(MessageConfig.prefix() + "§cOnly the owner may alter this structure; occupied cores must be emptied first.§r"); return;
                }
            } catch (RuntimeException corrupt) { event.setCancelled(true); return; }
        }
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent event) {
        for (Block core:claimsNear(event.getBlock())) if (!permitted(event.getPlayer(),core)) { event.setCancelled(true); return; }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void afterBreak(BlockBreakEvent event) {
        // BlockBreakEvent runs while the old block/PDC is still available.
        try {
            Block core=event.getBlock();
            var inventories=new MachineInventoryStorage(plugin.getName().toLowerCase(Locale.ROOT),"barrel_inventory_storage",null);
            if (identity(core).isPresent()) { closeCore(core); storage.remove(core); inventories.remove(core); }
            else if (core.getChunk().getPersistentDataContainer().has(inventories.key(core))
                    && Arrays.stream(inventories.load(core).items()).allMatch(Objects::isNull)) inventories.remove(core);
        } catch (RuntimeException ignored) { }
    }
    private void protect(List<Block> blocks) { blocks.removeIf(block -> !claimsNear(block).isEmpty()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onExplosion(EntityExplodeEvent event) { protect(event.blockList()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onExplosion(BlockExplodeEvent event) { protect(event.blockList()); }
    private boolean piston(List<Block> blocks,BlockFace direction) {
        return blocks.stream().anyMatch(block -> !claimsNear(block).isEmpty() || !claimsNear(block.getRelative(direction)).isEmpty());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onPiston(BlockPistonExtendEvent event) { if (piston(event.getBlocks(),event.getDirection())) event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onPiston(BlockPistonRetractEvent event) { if (piston(event.getBlocks(),event.getDirection())) event.setCancelled(true); }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        var view = views.get(event.getInventory());
        if (view != null && view.lease().playerId().equals(event.getPlayer().getUniqueId())) release(event.getInventory());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        for (var entry : List.copyOf(views.entrySet())) if (entry.getValue().lease().playerId().equals(event.getPlayer().getUniqueId())) release(entry.getKey());
    }
    @EventHandler public void onUnload(ChunkUnloadEvent event) {
        for (var entry : List.copyOf(views.entrySet())) if (entry.getValue().core().getChunk().equals(event.getChunk())) closeCore(entry.getValue().core());
    }
    public void closeCore(Block core) {
        for (var entry : List.copyOf(views.entrySet())) if (entry.getValue().core().equals(core)) {
            for (var viewer : List.copyOf(entry.getKey().getViewers())) viewer.closeInventory(); release(entry.getKey());
        }
    }
    public void close() {
        closed = true;
        for (var view : List.copyOf(views.values())) closeCore(view.core());
        views.clear(); custom.clear(); leases.clear();
        pendingClaims.clear();
    }
}
