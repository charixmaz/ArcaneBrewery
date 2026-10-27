package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.MachineAutomationHolder;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class MaterialDiscoveryListenerTest {
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,java.util.function.BiFunction<String,Object[],Object> calls) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->switch(m.getName()){case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->type.getSimpleName();default->calls.apply(m.getName(),a);});
    }
    static final class Fixture implements AutoCloseable {
        final UUID id=UUID.randomUUID();final ArrayDeque<Runnable> main=new ArrayDeque<>(),worker=new ArrayDeque<>();final List<String> messages=new ArrayList<>();
        ItemStack[] contents={};ItemStack cursor;boolean virtual,online=true;int writes;
        final Map<UUID,Set<MaterialKey>> stored=new HashMap<>();
        final PlayerInventory inventory=proxy(PlayerInventory.class,(n,a)->n.equals("getContents")?contents.clone():null);
        final Inventory top=proxy(Inventory.class,(n,a)->switch(n){case "getHolder"->virtual?new MachineAutomationHolder(){public Inventory getInventory(){return null;}}:null;case "getSize"->54;default->throw new AssertionError(n);});
        final InventoryView view=proxy(InventoryView.class,(n,a)->switch(n){case "getTopInventory"->top;case "getPlayer"->this.player;case "convertSlot"->a[0];default->throw new AssertionError(n);});
        final Player player=proxy(Player.class,(n,a)->switch(n){case "getUniqueId"->id;case "isOnline"->online;case "getInventory"->inventory;case "getItemOnCursor"->cursor;case "getOpenInventory"->view;case "sendMessage"->{messages.add((String)a[0]);yield null;}default->throw new AssertionError(n);});
        final Plugin plugin=proxy(Plugin.class,(n,a)->switch(n){case "isEnabled"->true;case "getName"->"ArcaneBrewery";case "getLogger"->java.util.logging.Logger.getLogger("test");default->throw new AssertionError(n);});
        final MaterialDiscoveryListener listener;
        Fixture(){
            var repo=(MaterialDiscoveryRepository)(uuid,keys,time)->{writes++;var known=stored.computeIfAbsent(uuid,x->new HashSet<>());var added=new HashSet<>(keys);added.removeAll(known);known.addAll(keys);return new MaterialDiscoveryRepository.Update(added,known);};
            var catalog=new MaterialCatalog(new IngredientItemAdapter(BuiltInIngredients.registry(),new IngredientMetadataCodec("arcanebrewery")));
            listener=new MaterialDiscoveryListener(plugin,catalog,new MaterialDiscoveryRuntime(repo,worker::add),main::add,uuid->online&&uuid.equals(id)?player:null,false);
        }
        void drain(){int budget=100;while(!main.isEmpty()||!worker.isEmpty()){assertTrue(budget-->0);if(!worker.isEmpty())worker.removeFirst().run();if(!main.isEmpty())main.removeFirst().run();}}
        InventoryClickEvent click(InventoryAction action){return new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,action);}
        public void close(){listener.close();}
    }
    @Test void joiningOrReconciliationUsesExistingInventoryAndCoalescesPendingObservations() {
        try(var f=new Fixture()) {
            f.contents=new ItemStack[]{TestItems.stack(Material.WHEAT,64),TestItems.stack(Material.BLAZE_ROD,1)};
            var first=f.listener.reconcile(f.player);var duplicate=f.listener.reconcile(f.player);
            assertSame(first,duplicate);assertEquals(0,f.writes);assertEquals(1,f.worker.size());f.drain();
            assertEquals(2,first.join().size());assertEquals(1,f.writes);assertEquals(2,f.messages.size());
            f.listener.reconcile(f.player);f.drain();assertEquals(2,f.messages.size());assertEquals(1,f.writes);
        }
    }
    @Test void canceledGuideAndPreviewTransfersDoNotUnlockViewedItems() {
        try(var f=new Fixture()) {
            var canceled=f.click(InventoryAction.PICKUP_ALL);canceled.setCancelled(true);f.listener.click(canceled);assertTrue(f.main.isEmpty());
            f.virtual=true;f.listener.click(f.click(InventoryAction.PICKUP_ALL));assertTrue(f.main.isEmpty());
            f.virtual=false;f.listener.click(f.click(InventoryAction.NOTHING));assertTrue(f.main.isEmpty());
            // Even a successful hint cannot invent the viewed container's item: resulting possession is empty.
            f.listener.click(f.click(InventoryAction.PICKUP_ALL));f.drain();assertTrue(f.stored.get(f.id).isEmpty());assertTrue(f.messages.isEmpty());
            f.contents=new ItemStack[]{TestItems.stack(Material.BLAZE_ROD,1)};
            f.listener.click(f.click(InventoryAction.PICKUP_ALL));f.drain();assertEquals(1,f.stored.get(f.id).size());assertEquals(2,f.messages.size());
        }
    }
    @Test void disconnectAndShutdownReleaseTemporaryRequestsWithoutCorruptingProgress() {
        try(var f=new Fixture()) {
            f.contents=new ItemStack[]{TestItems.stack(Material.WHEAT,1)};var pending=f.listener.reconcile(f.player);
            f.online=false;f.listener.quit(new PlayerQuitEvent(f.player,(String)null));assertTrue(pending.isCompletedExceptionally());f.drain();assertTrue(f.messages.isEmpty());
            f.online=true;var reconnect=f.listener.reconcile(f.player);f.drain();assertEquals(1,reconnect.join().size());assertTrue(f.messages.isEmpty());
            f.contents=new ItemStack[]{TestItems.stack(Material.BLAZE_ROD,1)};var closing=f.listener.reconcile(f.player);f.listener.close();assertTrue(closing.isCompletedExceptionally());
            assertTrue(f.listener.reconcile(f.player).isCompletedExceptionally());
        }
    }
    @Test void actualOwnedMachineInputsReconcileButDecorationsAreExcluded() {
        try(var f=new Fixture()) {
            f.virtual=true;var icon=TestItems.stack(Material.BLAZE_ROD,1);icon.edit(DisplayOnlyItems::mark);
            f.listener.reconcileOwnedInputs(f.player,new ItemStack[]{icon,TestItems.stack(Material.IRON_NUGGET,1)});f.drain();
            var known=f.stored.get(f.id);assertEquals(Set.of(new MaterialKey(MaterialKey.Kind.CATALYST,"arcanebrewery:mining_catalyst")),known);
        }
    }
}
