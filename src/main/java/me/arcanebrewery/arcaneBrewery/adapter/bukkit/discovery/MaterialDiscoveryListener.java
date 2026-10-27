package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.MachineAutomationHolder;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.*;
/** Main-thread snapshots, one bounded persistence request per player, no container/preview scans. */
public final class MaterialDiscoveryListener implements Listener,AutoCloseable {
    private static final int PLAYERS_PER_TICK=8;
    private final Plugin plugin;private final MaterialCatalog catalog;private final MaterialDiscoveryRuntime runtime;
    private final java.util.function.Consumer<Runnable> main;private final java.util.function.Function<UUID,Player> online;
    private final Map<UUID,Session> sessions=new LinkedHashMap<>();
    private final Set<UUID> scheduled=new HashSet<>();private final BukkitTask fallback;
    private final ArrayDeque<UUID> roundRobin=new ArrayDeque<>();private final Set<UUID> tracked=new HashSet<>();
    private boolean closed;
    private static final class Session {
        Set<MaterialKey> known=Set.of();final Set<MaterialKey> pending=new HashSet<>();boolean loaded,busy;
        CompletableFuture<Set<MaterialKey>> settled=new CompletableFuture<>();
    }
    public MaterialDiscoveryListener(Plugin plugin,MaterialCatalog catalog,MaterialDiscoveryRuntime runtime) {
        this(plugin,catalog,runtime,task->Bukkit.getScheduler().runTask(plugin,task),Bukkit::getPlayer,true);
    }
    MaterialDiscoveryListener(Plugin plugin,MaterialCatalog catalog,MaterialDiscoveryRuntime runtime,java.util.function.Consumer<Runnable> main,
            java.util.function.Function<UUID,Player> online,boolean timers) {
        this.plugin=plugin;this.catalog=catalog;this.runtime=runtime;this.main=main;this.online=online;
        fallback=timers?Bukkit.getScheduler().runTaskTimer(plugin,this::fallback,20,20):null;
        if(timers)for(var player:Bukkit.getOnlinePlayers()) schedule(player);
    }
    public MaterialCatalog catalog(){return catalog;}
    public CompletableFuture<Set<MaterialKey>> reconcile(Player player) {
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("discovery service closed"));
        if(tracked.add(player.getUniqueId()))roundRobin.addLast(player.getUniqueId());
        var s=sessions.computeIfAbsent(player.getUniqueId(),id->new Session());
        // Always reconcile actual possession before displaying locks. Never gate machine usability on this cache.
        for(var item:player.getInventory().getContents()) s.pending.addAll(catalog.classify(item));
        if(!(player.getOpenInventory().getTopInventory().getHolder() instanceof MachineAutomationHolder)) s.pending.addAll(catalog.classify(player.getItemOnCursor()));
        s.pending.removeAll(s.known);
        if(s.loaded && s.pending.isEmpty() && !s.busy)return CompletableFuture.completedFuture(s.known);
        if(s.settled.isDone())s.settled=new CompletableFuture<>();
        flush(player.getUniqueId(),s);return s.settled;
    }
    /** Only verified, committed owner-machine INPUT slots may be supplied; never a whole virtual view. */
    public void reconcileOwnedInputs(Player player,ItemStack[] inputs) {
        if(closed)return;
        reconcile(player);
        var s=sessions.get(player.getUniqueId());if(s==null)return;
        for(var item:inputs)s.pending.addAll(catalog.classify(item));s.pending.removeAll(s.known);
        if(s.settled.isDone())s.settled=new CompletableFuture<>();
        if(!s.pending.isEmpty())flush(player.getUniqueId(),s);
    }
    private void flush(UUID id,Session s) {
        if(s.busy || closed)return;
        var keys=s.pending.stream().limit(128).collect(java.util.stream.Collectors.toUnmodifiableSet());s.pending.removeAll(keys);s.busy=true;
        CompletableFuture<MaterialDiscoveryRepository.Update> operation;
        try {operation=runtime.obtain(id,keys);}catch(RuntimeException rejected){s.busy=false;s.pending.addAll(keys);s.settled.completeExceptionally(rejected);return;}
        operation.whenComplete((update,failure)->{
            if(closed || !plugin.isEnabled())return;
            main.accept(()->{
                if(sessions.get(id)!=s)return; // a reconnect is a different temporary session
                s.busy=false;
                if(failure!=null){s.pending.addAll(keys);s.settled.completeExceptionally(failure);plugin.getLogger().warning("Material discoveries could not be persisted; retained for retry.");return;}
                s.known=update.known();s.loaded=true;s.pending.removeAll(s.known);
                var recipient=online.apply(id);if(recipient!=null) catalog.notification(update.newlyUnlocked()).forEach(recipient::sendMessage);
                if(!s.pending.isEmpty())flush(id,s);else s.settled.complete(s.known);
            });
        });
    }
    private void schedule(Player player) {
        UUID id=player.getUniqueId();if(closed || !scheduled.add(id))return;
        main.accept(()->{scheduled.remove(id);if(!closed && player.isOnline())reconcile(player);});
    }
    private void fallback() {
        int count=Math.min(PLAYERS_PER_TICK,roundRobin.size());
        for(int n=0;n<count;n++) {
            UUID id=roundRobin.removeFirst();var player=online.apply(id);
            if(player!=null && player.isOnline()){reconcile(player);roundRobin.addLast(id);}else tracked.remove(id);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e){if(e.getEntity() instanceof Player p)schedule(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void click(InventoryClickEvent e){if(e.getWhoClicked() instanceof Player p && MaterialAcquisitionPolicy.click(e))schedule(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drag(InventoryDragEvent e){if(e.getWhoClicked() instanceof Player p && MaterialAcquisitionPolicy.drag(e))schedule(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void harvest(PlayerHarvestBlockEvent e){schedule(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){schedule(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent e){schedule(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR) public void quit(PlayerQuitEvent e){tracked.remove(e.getPlayer().getUniqueId());roundRobin.remove(e.getPlayer().getUniqueId());var s=sessions.remove(e.getPlayer().getUniqueId());scheduled.remove(e.getPlayer().getUniqueId());if(s!=null)s.settled.completeExceptionally(new CancellationException("player disconnected"));}
    public void close(){closed=true;if(fallback!=null)fallback.cancel();for(var s:sessions.values())s.settled.completeExceptionally(new CancellationException("plugin shutdown"));sessions.clear();scheduled.clear();roundRobin.clear();tracked.clear();}
}
