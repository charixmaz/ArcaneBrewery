package me.arcanebrewery.arcaneBrewery.guide;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.discovery.DiscoveryQueryRuntime;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import me.arcanebrewery.arcaneBrewery.application.storage.DiscoveryRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

/** API boundary tests, not an emulation of native Paper inventory processing. */
class BrewingGuideInventoryTest {
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, BiFunction<String,Object[],Object> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> switch(m.getName()) {
            case "equals" -> p == a[0]; case "hashCode" -> System.identityHashCode(p); case "toString" -> type.getSimpleName();
            default -> calls.apply(m.getName(), a);
        });
    }
    private final UUID id = UUID.randomUUID();
    private final BrewingGuide.View holder = new BrewingGuide.View(id);
    private final Inventory top = proxy(Inventory.class, (name,args) -> switch(name) {
        case "getHolder" -> holder; case "getSize" -> 54;
        default -> throw new AssertionError("Unexpected inventory access: " + name);
    });
    private final Player player = proxy(Player.class, (name,args) -> switch(name) {
        case "getUniqueId" -> id; case "isOnline" -> true; case "getOpenInventory" -> view();
        default -> throw new AssertionError("Unexpected player mutation: " + name);
    });
    private InventoryView view() {
        return proxy(InventoryView.class, (name,args) -> switch(name) {
            case "getTopInventory" -> top; case "getPlayer" -> player; case "convertSlot" -> args[0];
            default -> throw new AssertionError(name);
        });
    }
    private BrewingGuide guide() {
        holder.inventory = top;
        var plugin = proxy(Plugin.class, (n,a) -> { throw new AssertionError(n); });
        var repository = proxy(DiscoveryRepository.class, (n,a) -> { throw new AssertionError(n); });
        return new BrewingGuide(plugin, new GuideCatalog(BuiltInIngredients.registry(), new ProcessingTimes(10000,60,120),8),
                new DiscoveryQueryRuntime(repository, Runnable::run));
    }
    @Test void topAndBottomInventoryGesturesAreCancelledWithoutMovingPlayerItems() {
        var guide = guide();
        for (int slot : new int[]{0, 53, 54, 70, -999}) {
            for (ClickType click : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
                    ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK,
                    ClickType.DROP, ClickType.CONTROL_DROP, ClickType.CREATIVE, ClickType.MIDDLE)) {
                var event = new InventoryClickEvent(view(), InventoryType.SlotType.CONTAINER, slot, click,
                        InventoryAction.MOVE_TO_OTHER_INVENTORY, 0);
                guide.click(event);
                assertTrue(event.isCancelled(), click + " at " + slot);
            }
        }
    }
    @Test void dragIsCancelledWithoutMutatingCursor() {
        var guide = guide();
        var item = TestItems.stack(Material.DIAMOND, 5);
        var event = new InventoryDragEvent(view(), item, item, false, Map.of(0, item));
        guide.drag(event);
        assertTrue(event.isCancelled());
        assertEquals(5, item.getAmount());
    }
    @Test void ordinaryInventoryIsUnaffected() {
        var guide = guide();
        var ordinary = proxy(Inventory.class, (n,a) -> n.equals("getHolder") ? null : 54);
        var otherView = proxy(InventoryView.class, (n,a) -> switch(n) {
            case "getTopInventory" -> ordinary; case "getPlayer" -> player; case "convertSlot" -> a[0];
            default -> throw new AssertionError(n);
        });
        var event = new InventoryClickEvent(otherView, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.NOTHING);
        guide.click(event);
        assertFalse(event.isCancelled());
    }
}
