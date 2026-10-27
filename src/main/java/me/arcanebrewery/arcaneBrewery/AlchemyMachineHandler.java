package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.distillery.MachineLocation;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.BukkitMachineAccess;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity.Kind;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

/** Main-thread coordinator. Every machine owns exactly one shared GUI and one atomic persisted snapshot. */
public final class AlchemyMachineHandler implements Listener {
    private static final int[] INPUTS = {2, 4, 6};
    private final JavaPlugin plugin;
    private final String namespace;
    private final AlchemyMachineItems items = new AlchemyMachineItems();
    private final AlchemyMachineStorage storage;
    private final AlchemyRuntimeSettings settings;
    private final AlchemyMachineService machineService;
    private final AlchemyMaintenanceService maintenanceService;
    private final BukkitMachineAccess access;
    private final Map<MachineLocation, Session> sessions = new HashMap<>();
    private final Map<MachineLocation, BukkitRunnable> tasks = new HashMap<>();
    private boolean shuttingDown;

    private static final class Holder implements me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.MachineGuiHolder {
        final MachineLocation key;
        Inventory inventory;
        Holder(MachineLocation key) { this.key = key; }
        public Inventory getInventory() { return inventory; }
    }
    private static final class Session {
        final Block core;
        final Inventory gui;
        AlchemyMachineSnapshot snapshot;
        byte[] persisted;
        boolean blocked;
        Session(Block core, Inventory gui, AlchemyMachineStorage.Loaded loaded) {
            this.core = core; this.gui = gui; this.snapshot = loaded.snapshot(); this.persisted = loaded.persisted();
        }
        boolean processing() {
            return snapshot.operation().map(op -> op.state().status() == AlchemyMachineState.Status.PROCESSING).orElse(false);
        }
    }

    public AlchemyMachineHandler(JavaPlugin plugin) {
        this(plugin, new AlchemyRuntimeSettings(
                AlchemyBalanceProfile.DEFAULT, Material.IRON_INGOT, Material.SPONGE));
    }

    public AlchemyMachineHandler(JavaPlugin plugin, AlchemyRuntimeSettings settings) {
        this(plugin,settings,((ArcaneBrewery)plugin).machineAccess());
    }
    public AlchemyMachineHandler(JavaPlugin plugin, AlchemyRuntimeSettings settings,BukkitMachineAccess access) {
        this.plugin = Objects.requireNonNull(plugin);
        this.settings = Objects.requireNonNull(settings);
        this.namespace = plugin.getName().toLowerCase(Locale.ROOT);
        this.storage = new AlchemyMachineStorage(namespace, items);
        this.machineService = new AlchemyMachineService(settings.balance());
        this.maintenanceService = new AlchemyMaintenanceService(settings.balance());
        this.access=Objects.requireNonNull(access);
        access.occupancy(Kind.INFUSER,this::occupied);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(PlayerInteractEvent event) {
        if (shuttingDown || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block core = event.getClickedBlock();
        if (!AlchemyStructureScanner.isValid(core)) return; // Leave normal enchanting entirely alone.
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!access.authorize(event.getPlayer(),core,Kind.INFUSER,false)) return;
        try {
            Session session = session(core);
            if (session.blocked) throw new IllegalStateException("machine unavailable");
            access.open(event.getPlayer(),core,Kind.INFUSER,() -> {
                recover(session);
                if (session.blocked) throw new IllegalStateException("machine unavailable");
                if (event.getPlayer().isSneaking()) maintain(event.getPlayer(),session);
                paint(session);
                return session.gui;
            });
            evictIdle(session);
        } catch (RuntimeException failure) {
            warn(core);
            event.getPlayer().sendMessage("§c[ArcaneBrewery] Machine data is unavailable; stored items have been retained.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Inventory gui = event.getView().getTopInventory();
        if (!(gui.getHolder() instanceof Holder holder)) return;
        Session session = sessions.get(holder.key);
        if (event.isCancelled()) return;
        if (shuttingDown || session == null || session.gui != gui || session.blocked
                || !access.active(gui,event.getWhoClicked().getUniqueId())
                || !AlchemyStructureScanner.isValid(session.core) || event instanceof InventoryCreativeEvent) {
            event.setCancelled(true);
            return;
        }
        if(event.isShiftClick()) {
            event.setCancelled(true);if(session.processing())return;
            try{me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ShiftTransfers.move(event,contents(gui),INPUTS,new int[]{0,1,2},i->1,(slot,item)->{
                if(slot==0){try{return new me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata(namespace).read(item.getItemMeta()).map(b->b.status()==me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState.Status.COMPLETED).orElse(false)&&new AlchemyPreparationMetadata(namespace).read(item.getItemMeta()).isEmpty();}catch(RuntimeException bad){return false;}}
                return BuiltInAlchemyCatalysts.resolveExternal(item.getType().getKey().toString()).isPresent();
            },next->commit(session,next,Optional.empty()));}catch(RuntimeException rejected){event.getWhoClicked().sendMessage("§eTransfer retained; machine unavailable.");}return;
        }
        var decision = AlchemyInventoryPolicy.click(event.getClickedInventory() == gui, event.getSlot(),
                session.processing(), event.getClick(), event.getAction());
        if (decision == AlchemyInventoryPolicy.Decision.PASS) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (decision == AlchemyInventoryPolicy.Decision.START) { start(player, session); return; }
        if (decision != AlchemyInventoryPolicy.Decision.EDIT) return;
        int index = event.getSlot() == 2 ? 0 : event.getSlot() == 4 ? 1 : 2;
        var move = AlchemySlotTransfer.move(gui.getItem(event.getSlot()), player.getItemOnCursor(),
                event.getClick() == ClickType.RIGHT, 1);
        ItemStack[] next = contents(gui);
        next[index] = move.slot();
        try {
            commit(session, next, Optional.empty()); // Any idle edit acknowledges/removes historical completion.
            player.setItemOnCursor(move.cursor()); // Only after the machine snapshot is accepted.
        } catch (RuntimeException failure) {
            fault(session);
            player.sendMessage("§c[ArcaneBrewery] Transfer rejected; machine items were retained.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder
                && AlchemyInventoryPolicy.dragTouchesMachine(event.getRawSlots(), 9)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Holder || event.getDestination().getHolder() instanceof Holder)
            event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder) || shuttingDown) return;
        Session session = sessions.get(holder.key);
        if (session == null || session.gui != event.getInventory()) return;
        // There is no stale GUI-to-disk write on close: every accepted edit was already persisted.
        Bukkit.getScheduler().runTask(plugin, () -> evictIdle(session));
    }

    private Session session(Block core) {
        MachineLocation key = key(core);
        Session existing = sessions.get(key);
        if (existing != null) return existing;
        var loaded = storage.load(core);
        ItemStack[] decoded = items.decode(loaded.snapshot().items());
        if (loaded.snapshot().operation().map(op -> op.state().status() == AlchemyMachineState.Status.PROCESSING).orElse(false))
            AlchemyMachineInputs.validateLocked(decoded, namespace, loaded.snapshot().operation().orElseThrow());
        else if (loaded.snapshot().operation().isPresent())
            AlchemyMachineInputs.validateCompleted(decoded, namespace, loaded.snapshot().operation().orElseThrow());
        var holder = new Holder(key);
        Inventory gui = Bukkit.createInventory(holder, 9, MessageConfig.title("infuser"));
        holder.inventory = gui;
        var session = new Session(core, gui, loaded);
        apply(gui, decoded);
        if (loaded.persisted() == null && storage.hasData(core))
            session.persisted = storage.save(core, null, session.snapshot); // Successful legacy import, no empty fallback.
        paint(session);
        sessions.put(key, session);
        return session;
    }

    private void start(Player player, Session session) {
        if (session.processing()) return;
        AlchemyMachineOperation operation;
        try { operation = AlchemyMachineInputs.start(contents(session.gui), namespace,
                System.currentTimeMillis(), settings.balance().processingDurationMillis()); }
        catch (RuntimeException invalid) {
            player.sendMessage("§c[ArcaneBrewery] Cannot start infusion: §f" + invalid.getMessage());
            return;
        }
        try {
            var captured=contents(session.gui);var baseMeta=captured[0].getItemMeta();
            var effectMetadata=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.EffectProfileMetadata(namespace);
            effectMetadata.captureInfusion(baseMeta);
            captured[0].setItemMeta(baseMeta);
            commit(session, captured, Optional.of(operation));
            if(plugin instanceof ArcaneBrewery brewery)brewery.reconcileMaterialInputs(player,captured);
            schedule(session);
            var assessment = machineService.assess(
                    AlchemyMachineInputs.catalysts(contents(session.gui)), new AlchemyMachineCondition(session.snapshot.condition().integrity(),0));
            player.sendMessage("§d[ArcaneBrewery] Infusion started. Instability: §f"
                    + Math.round(assessment.instability()) + "/100 §7| Integrity: §f"
                    + Math.round(session.snapshot.condition().integrity()) + "/100");
        } catch (RuntimeException failure) { fault(session); }
    }

    private boolean maintain(Player player, Session session) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean repair = held.getType() == settings.repairMaterial();
        if (!repair) return false;

        if (session.processing()) {
            player.sendMessage("§c[ArcaneBrewery] The infuser cannot be serviced while processing.");
            return true;
        }

        AlchemyMachineCondition current = session.snapshot.condition();
        AlchemyMachineCondition next = maintenanceService.repair(current);
        if (next.equals(current)) {
            player.sendMessage("§7[ArcaneBrewery] Infuser integrity is already full.");
            return true;
        }

        commit(session, contents(session.gui), session.snapshot.operation(), next);
        if (held.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            held.setAmount(held.getAmount() - 1);
        }
        player.sendMessage("§a[ArcaneBrewery] Infuser repaired to §f" + Math.round(next.integrity()) + "/100§a integrity.");
        return true;
    }

    private void recover(Session session) {
        if (!session.processing() || !AlchemyStructureScanner.isValid(session.core)) return;
        if (session.snapshot.operation().orElseThrow().state().ready(System.currentTimeMillis())) {
            complete(session); stop(key(session.core));
        }
        else schedule(session);
    }

    private void schedule(Session session) {
        MachineLocation key = key(session.core);
        if (tasks.containsKey(key)) return;
        BukkitRunnable task = new BukkitRunnable() {
            public void run() {
                try {
                    if (sessions.get(key) != session || session.blocked || !session.processing()
                            || !AlchemyStructureScanner.isValid(session.core)) {
                        detach(session); return;
                    }
                    if (session.snapshot.operation().orElseThrow().state().ready(System.currentTimeMillis())) {
                        complete(session);
                        stop(key);
                        evictIdle(session);
                    } else {
                        paint(session);
                        session.core.getWorld().spawnParticle(Particle.ENCHANT,
                                session.core.getLocation().add(0.5, 1.0, 0.5), 10, 0.35, 0.25, 0.35, 0.05);
                    }
                } catch (RuntimeException failure) { fault(session); }
            }
        };
        tasks.put(key, task);
        try { task.runTaskTimer(plugin, 1L, 20L); }
        catch (RuntimeException failure) { tasks.remove(key); throw failure; }
    }

    private void complete(Session session) {
        if (session.blocked || !session.processing() || !AlchemyStructureScanner.isValid(session.core)) return;
        var operation = session.snapshot.operation().orElseThrow();
        long now = System.currentTimeMillis();
        AlchemyFailureAssessment assessment = machineService.assess(
                operation.preparation(), new AlchemyMachineCondition(session.snapshot.condition().integrity(),0));
        AlchemyMachineCondition nextCondition = session.snapshot.condition().wear(
                assessment.integrityDamage(), 0);

        ItemStack[] result;
        Optional<AlchemyMachineOperation> completedOperation;
        if (assessment.producesPreparedBase()) {
            result = AlchemyMachineInputs.complete(contents(session.gui), namespace, operation, now);
            completedOperation = Optional.of(operation.complete(now));
        } else {
            result = AlchemyMachineInputs.fail(
                    contents(session.gui), namespace, operation, now, assessment.outcome());
            completedOperation = Optional.empty();
        }

        if(result[0]!=null && result[0].hasItemMeta()) {
            var meta=result[0].getItemMeta();
            new me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyBatchConditionsMetadata(namespace).write(meta,session.snapshot.condition(),assessment);
            result[0].setItemMeta(meta);
        }
        commit(session, result, completedOperation, nextCondition);
        feedback(session, assessment);
    }

    private void feedback(Session session, AlchemyFailureAssessment assessment) {
        String message = switch (assessment.outcome()) {
            case STABLE_SUCCESS -> "§d[ArcaneBrewery] Infusion complete. The base is ready for maturation.";
            case UNSTABLE_SUCCESS -> "§6[ArcaneBrewery] Infusion succeeded, but the infuser was strained.";
            case FAILED_INFUSION -> "§c[ArcaneBrewery] Infusion failed. Catalysts were consumed; the base survived.";
            case CRITICAL_FAILURE -> "§4§l[ArcaneBrewery] Critical reaction! The batch was destroyed.";
        };
        for (var viewer : session.gui.getViewers()) viewer.sendMessage(message);

        var world = session.core.getWorld();
        var location = session.core.getLocation().add(0.5, 1.0, 0.5);
        switch (assessment.outcome()) {
            case STABLE_SUCCESS -> world.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.4f);
            case UNSTABLE_SUCCESS -> {
                world.spawnParticle(Particle.SMOKE, location, 18, 0.35, 0.35, 0.35, 0.03);
                world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
            }
            case FAILED_INFUSION -> {
                world.spawnParticle(Particle.SMOKE, location, 30, 0.45, 0.45, 0.45, 0.05);
                world.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
            }
            case CRITICAL_FAILURE -> world.createExplosion(
                    location, settings.balance().criticalExplosionPower(), false, false);
        }
    }

    private void commit(Session session, ItemStack[] next, Optional<AlchemyMachineOperation> operation) {
        commit(session, next, operation, session.snapshot.condition());
    }

    private void commit(Session session, ItemStack[] next, Optional<AlchemyMachineOperation> operation,
            AlchemyMachineCondition condition) {
        var snapshot = new AlchemyMachineSnapshot(operation, condition, items.encode(next));
        byte[] persisted = storage.save(session.core, session.persisted, snapshot);
        session.persisted = persisted;
        session.snapshot = snapshot;
        apply(session.gui, next);
        paint(session);
    }

    private void apply(Inventory gui, ItemStack[] contents) {
        for (int i = 0; i < 3; i++) gui.setItem(INPUTS[i], contents[i]);
    }
    private ItemStack[] contents(Inventory gui) {
        return Arrays.stream(INPUTS).mapToObj(gui::getItem).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }
    private void paint(Session session) {
        try { render(session); }
        catch (RuntimeException visualFailure) { plugin.getLogger().warning("Infuser display refresh failed; saved contents retained."); }
    }
    private void render(Session session) {
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 2 || slot == 4 || slot == 6) continue;
            var item = new ItemStack(slot == 8 ? (session.processing() ? Material.CLOCK : Material.NETHER_STAR)
                    : Material.PURPLE_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems.mark(meta);
                String label=switch(slot) {
                    case 0 -> "Base → Slot 3";
                    case 1 -> "Catalyst I → Slot 5";
                    case 3 -> "Catalyst II → Slot 7 (Optional)";
                    case 5 -> "Owner: " + access.ownerLabel(session.core);
                    case 7 -> session.processing()?"PROCESSING":idleStatus(session);
                    default -> session.processing()?"Infusion Processing":idleStatus(session).equals("READY")?"Infusion Complete":"Start Infusion";
                };
                meta.setDisplayName("§d§l"+label+"§r");
                var lore=new ArrayList<String>();
                if (slot==0) lore.add("§7Insert one completed cultivated base.§r");
                if (slot==1 || slot==3) lore.add("§7One dose per slot; select two identical catalysts for a double dose.§r");
                if (slot==8) {
                    lore.add(idleStatus(session).equals("READY")?"§7Take the prepared base from slot 3.§r":"§7Click to validate inputs and start infusion.§r");
                    lore.add("§7Integrity: §f"+Math.round(session.snapshot.condition().integrity())+"/100§r");
                }
                if (session.processing() && (slot==7 || slot==8)) {
                    var state=session.snapshot.operation().orElseThrow().state();
                    long elapsed=Math.max(0,System.currentTimeMillis()-state.startedAtEpochMillis());
                    lore.add("§7Progress: §b"+Math.min(100,elapsed*100/state.durationMillis())+"%§r");
                    lore.add("§7Remaining: §f"+Math.max(0,(state.durationMillis()-elapsed+999)/1000)+"s§r");
                    lore.add("§eAll machine inputs are locked.§r");
                }
                meta.setLore(lore);
                me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta);
                item.setItemMeta(meta);
            }
            session.gui.setItem(slot, item);
        }
    }
    private String idleStatus(Session session) {
        ItemStack base=session.gui.getItem(2);
        if (!AlchemyMachineItems.empty(base) && base.getItemMeta()!=null
                && new AlchemyPreparationMetadata(namespace).read(base.getItemMeta()).isPresent()) return "READY";
        return Arrays.stream(contents(session.gui)).allMatch(AlchemyMachineItems::empty)?"EMPTY":"IDLE";
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        for (Session session : List.copyOf(sessions.values())) {
            if (!touches(session.core, block)) continue;
            if (session.processing() || occupied(session.core)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c[ArcaneBrewery] This infuser is occupied or processing. Remove its contents before breaking the structure.");
                return;
            }
            detach(session);
        }

        if (block.getType() == Material.ENCHANTING_TABLE && occupied(block)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[ArcaneBrewery] Remove machine items before breaking its core.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (int[] offset : new int[][]{{0,0,0}, {0,1,0}, {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1}})
                resume(block.getRelative(offset[0], offset[1], offset[2]));
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) { protectExplosion(event.blockList()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) { protectExplosion(event.blockList()); }
    private void protectExplosion(List<Block> blocks) {
        blocks.removeIf(block -> block.getType() == Material.ENCHANTING_TABLE && occupied(block));
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Session session : List.copyOf(sessions.values()))
                if (!AlchemyStructureScanner.isValid(session.core)) detach(session);
        });
    }
    private boolean occupied(Block core) {
        try {
            if (!sessions.containsKey(key(core)) && !storage.hasData(core)) return false;
            Session session = session(core);
            return session.blocked || session.processing() || Arrays.stream(contents(session.gui)).anyMatch(i -> !AlchemyMachineItems.empty(i));
        } catch (RuntimeException corrupt) { return true; } // Never destroy a quarantined inventory.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnload(ChunkUnloadEvent event) {
        for (Session session : List.copyOf(sessions.values())) {
            if (session.core.getWorld().equals(event.getWorld()) && session.core.getX() >> 4 == event.getChunk().getX()
                    && session.core.getZ() >> 4 == event.getChunk().getZ()) detach(session);
        }
    }
    @EventHandler
    public void onLoad(ChunkLoadEvent event) {
        // A structure can span a chunk border; reload must wake the neighboring loaded core too.
        for (int x = event.getChunk().getX() - 1; x <= event.getChunk().getX() + 1; x++)
            for (int z = event.getChunk().getZ() - 1; z <= event.getChunk().getZ() + 1; z++) {
                if (!event.getWorld().isChunkLoaded(x, z)) continue;
                for (var tile : event.getWorld().getChunkAt(x, z).getTileEntities())
                    if (tile.getType() == Material.ENCHANTING_TABLE) resume(tile.getBlock());
            }
    }
    public void resumeLoadedMachines() {
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks())
            for (var tile : chunk.getTileEntities()) if (tile.getType() == Material.ENCHANTING_TABLE) resume(tile.getBlock());
    }
    private void resume(Block core) {
        if (shuttingDown || !AlchemyStructureScanner.isValid(core)) return;
        try {
            if (!storage.hasData(core)) return;
            Session session = session(core);
            recover(session);
            evictIdle(session);
        } catch (RuntimeException failure) { warn(core); }
    }
    private void fault(Session session) {
        session.blocked = true;
        warn(session.core);
        stop(key(session.core));
        Bukkit.getScheduler().runTask(plugin, () -> detach(session));
    }
    private void warn(Block core) {
        plugin.getLogger().warning("Alchemy machine locked; original persisted data retained at "
                + core.getWorld().getName() + " " + core.getX() + "," + core.getY() + "," + core.getZ());
    }
    private void stop(MachineLocation key) {
        var task = tasks.remove(key);
        if (task != null) task.cancel();
    }
    private void evictIdle(Session session) {
        if (!session.processing() && session.gui.getViewers().isEmpty()) {
            stop(key(session.core)); sessions.remove(key(session.core), session);
        }
    }
    private void detach(Session session) {
        stop(key(session.core));
        for (var viewer : List.copyOf(session.gui.getViewers())) viewer.closeInventory();
        sessions.remove(key(session.core), session);
    }
    public void shutdown() {
        shuttingDown = true;
        for (Session session : List.copyOf(sessions.values())) detach(session);
        tasks.values().forEach(BukkitRunnable::cancel); tasks.clear(); sessions.clear();
    }
    private MachineLocation key(Block core) {
        return new MachineLocation(core.getWorld().getUID(), core.getX(), core.getY(), core.getZ());
    }
    private boolean touches(Block core, Block changed) {
        if (!core.getWorld().equals(changed.getWorld())) return false;
        int x = core.getX() - changed.getX(), y = core.getY() - changed.getY(), z = core.getZ() - changed.getZ();
        return y == 0 && Math.abs(x) + Math.abs(z) <= 1 || y == 1 && x == 0 && z == 0;
    }
}
