package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.MachineAutomationHolder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MaterialAcquisitionPolicyTest {
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,java.util.function.BiFunction<String,Object[],Object> methods) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->switch(m.getName()){case "equals"->p==a[0];case "hashCode"->System.identityHashCode(p);case "toString"->type.getSimpleName();default->methods.apply(m.getName(),a);});
    }
    private InventoryView view(boolean virtual) {
        var holder=new MachineAutomationHolder(){public Inventory getInventory(){return null;}};
        var top=proxy(Inventory.class,(n,a)->n.equals("getHolder")?(virtual?holder:null):54);
        var player=proxy(Player.class,(n,a)->n.equals("getUniqueId")?UUID.randomUUID():null);
        return proxy(InventoryView.class,(n,a)->switch(n){case "getTopInventory"->top;case "getPlayer"->player;case "convertSlot"->a[0];default->throw new AssertionError(n);});
    }
    @Test void canceledTransfersAndVirtualViewsNeverProvideAcquisitionHints() {
        var normal=new InventoryClickEvent(view(false),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        assertTrue(MaterialAcquisitionPolicy.click(normal));normal.setCancelled(true);assertFalse(MaterialAcquisitionPolicy.click(normal));
        var icon=new InventoryClickEvent(view(true),InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        assertFalse(MaterialAcquisitionPolicy.click(icon));
    }
    @Test void viewingAndPreviewNoOpsDoNotScheduleAnUnlock() {
        for(var action:List.of(InventoryAction.NOTHING,InventoryAction.UNKNOWN))assertFalse(MaterialAcquisitionPolicy.click(new InventoryClickEvent(view(false),InventoryType.SlotType.RESULT,0,ClickType.LEFT,action)));
        // A craft/trade result click is only a hint; the listener reads possession on the following server tick.
        assertTrue(MaterialAcquisitionPolicy.click(new InventoryClickEvent(view(false),InventoryType.SlotType.RESULT,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY)));
    }
}
