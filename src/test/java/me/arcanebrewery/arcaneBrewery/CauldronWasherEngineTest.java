package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import static org.junit.jupiter.api.Assertions.*;

class CauldronWasherEngineTest {
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> switch (method.getName()) {
            case "equals" -> p == args[0];
            case "hashCode" -> System.identityHashCode(p);
            case "toString" -> type.getSimpleName();
            default -> calls.apply(method.getName(), args);
        });
    }
    private static ItemStack[] copy(ItemStack[] values) {
        return Arrays.stream(values).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
    }
    private static final class Fixture {
        final IngredientItemAdapter items = new IngredientItemAdapter(BuiltInIngredients.registry(), new IngredientMetadataCodec("arcanebrewery"));
        final CauldronWasherEngine washer = new CauldronWasherEngine(items);
        ItemStack[] storage = new ItemStack[36];
        final int heldSlot = 3;
        Material material = Material.WATER_CAULDRON;
        int level = 3, inventoryWrites, waterWrites, sounds;
        boolean failInventory, failWater;
        final List<String> messages = new ArrayList<>();
        final PlayerInventory inventory = proxy(PlayerInventory.class, (n, a) -> switch (n) {
            case "getItemInMainHand" -> storage[heldSlot];
            case "getHeldItemSlot" -> heldSlot;
            case "getStorageContents" -> copy(storage);
            case "setStorageContents" -> {
                inventoryWrites++;
                storage = copy((ItemStack[]) a[0]);
                if (failInventory) { failInventory = false; throw new IllegalStateException("inventory update failed"); }
                yield null;
            }
            default -> throw new AssertionError("Unexpected inventory call: " + n);
        });
        final World world = proxy(World.class, (n, a) -> switch (n) {
            case "playSound" -> { sounds++; yield null; }
            default -> throw new AssertionError(n);
        });
        final Player player = proxy(Player.class, (n, a) -> switch (n) {
            case "getInventory" -> inventory;
            case "getWorld" -> world;
            case "sendMessage" -> { messages.add((String) a[0]); yield null; }
            default -> throw new AssertionError(n);
        });
        final Block block = proxy(Block.class, (n, a) -> switch (n) {
            case "getType" -> material;
            case "getBlockData" -> data(level);
            case "getLocation" -> new Location(world, 1, 64, 1);
            case "setType" -> { material = (Material) a[0]; level = 0; writeWater(); yield null; }
            case "setBlockData" -> { material = Material.WATER_CAULDRON; level = ((Levelled) a[0]).getLevel(); writeWater(); yield null; }
            default -> throw new AssertionError(n);
        });
        void writeWater() {
            waterWrites++;
            if (failWater) { failWater = false; throw new IllegalStateException("water update failed"); }
        }
        Levelled data(int value) {
            int[] current = {value};
            return proxy(Levelled.class, (n, a) -> switch (n) {
                case "getLevel" -> current[0];
                case "setLevel" -> { current[0] = (Integer) a[0]; yield null; }
                case "clone" -> data(current[0]);
                case "getMaterial" -> Material.WATER_CAULDRON;
                default -> throw new AssertionError(n);
            });
        }
        Fixture(int amount) {
            Arrays.setAll(storage, i -> TestItems.stack(Material.STONE, 64));
            storage[heldSlot] = TestItems.stack(Material.POTATO, amount);
        }
        PlayerInteractEvent event() { return event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND); }
        PlayerInteractEvent event(Action action, EquipmentSlot hand) {
            return new PlayerInteractEvent(player, action, storage[heldSlot], block, BlockFace.UP, hand, null);
        }
        int purity(int slot) { return IngredientPurityEngine.getPurity(items, storage[slot]); }
        int potatoCount() { return Arrays.stream(storage).filter(Objects::nonNull).filter(i -> i.getType() == Material.POTATO).mapToInt(ItemStack::getAmount).sum(); }
        void assertUnchanged(int count) {
            assertEquals(count, potatoCount()); assertEquals(0, purity(heldSlot));
            assertEquals(3, level); assertEquals(Material.WATER_CAULDRON, material);
            assertEquals(0, inventoryWrites); assertEquals(0, waterWrites); assertEquals(0, sounds);
        }
    }

    @Test void fullInventoryWithStackRejectsWithoutConsumingIngredientOrWater() {
        var f = new Fixture(12); f.washer.onIngredientWash(f.event());
        f.assertUnchanged(12);
        assertTrue(f.messages.getFirst().contains("Make inventory room"));
    }
    @Test void singleHeldItemReplacesItselfEvenWithEveryOtherSlotFull() {
        var f = new Fixture(1); f.washer.onIngredientWash(f.event());
        assertEquals(1, f.potatoCount()); assertEquals(25, f.purity(f.heldSlot));
        assertEquals(2, f.level); assertEquals(1, f.inventoryWrites); assertEquals(1, f.waterWrites);
    }
    @Test void stackUsesAnEmptyStorageSlotAndPreservesTotalQuantity() {
        var f = new Fixture(12); f.storage[8] = null; f.washer.onIngredientWash(f.event());
        assertEquals(12, f.potatoCount()); assertEquals(11, f.storage[f.heldSlot].getAmount());
        assertEquals(0, f.purity(f.heldSlot)); assertEquals(1, f.storage[8].getAmount()); assertEquals(25, f.purity(8));
        assertEquals(2, f.level);
    }
    @Test void fullInventoryCanMergeIntoAMatchingPartlyFilledWashedStack() {
        var f = new Fixture(12);
        f.storage[8] = IngredientPurityEngine.processWashCycle(f.items, f.storage[f.heldSlot]); f.storage[8].setAmount(63);
        f.washer.onIngredientWash(f.event());
        assertEquals(75, f.potatoCount()); assertEquals(11, f.storage[f.heldSlot].getAmount());
        assertEquals(64, f.storage[8].getAmount()); assertEquals(25, f.purity(8)); assertEquals(2, f.level);
    }
    @Test void fullStacksAndDifferentPuritiesDoNotProvidePhantomSpace() {
        for (boolean full : List.of(false, true)) {
            var f = new Fixture(12);
            var washed = IngredientPurityEngine.processWashCycle(f.items, f.storage[f.heldSlot]);
            if (!full) washed = IngredientPurityEngine.processWashCycle(f.items, washed);
            washed.setAmount(full ? 64 : 1); f.storage[8] = washed;
            int count = f.potatoCount(); f.washer.onIngredientWash(f.event()); f.assertUnchanged(count);
            assertEquals(full ? 25 : 50, f.purity(8));
        }
    }
    @Test void finalWaterLevelBecomesEmptyAndNoFurtherResourcesAreConsumed() {
        var f = new Fixture(1); f.level = 1; f.washer.onIngredientWash(f.event());
        assertEquals(Material.CAULDRON, f.material); assertEquals(0, f.level); assertEquals(25, f.purity(f.heldSlot));
        f.washer.onIngredientWash(f.event()); assertEquals(1, f.inventoryWrites); assertEquals(1, f.waterWrites);
    }
    @Test void repeatedWashesStopAtOneHundredWithoutConsumingMoreWater() {
        var f = new Fixture(1);
        for (int i = 1; i <= 4; i++) { f.level = 3; f.material = Material.WATER_CAULDRON; f.washer.onIngredientWash(f.event()); assertEquals(i * 25, f.purity(f.heldSlot)); }
        f.washer.onIngredientWash(f.event());
        assertEquals(4, f.inventoryWrites); assertEquals(4, f.waterWrites); assertEquals(2, f.level);
        assertFalse(f.messages.toString().contains("Contaminants"));
    }
    @Test void priorBlockOrItemDenialAndFullCancellationCannotWashOrOverrideDecisions() {
        for (int mode : List.of(0, 1, 2)) {
            var f = new Fixture(1); var event = f.event();
            if (mode == 0) event.setUseInteractedBlock(Event.Result.DENY);
            if (mode == 1) event.setUseItemInHand(Event.Result.DENY);
            if (mode == 2) event.setCancelled(true);
            var block = event.useInteractedBlock(); var item = event.useItemInHand();
            f.washer.onIngredientWash(event); f.assertUnchanged(1);
            assertEquals(block, event.useInteractedBlock()); assertEquals(item, event.useItemInHand()); assertTrue(f.messages.isEmpty());
        }
    }
    @Test void defaultAndExplicitlyAllowedInteractionsStillWorkAndSuppressVanillaEating() {
        for (var permission : List.of(Event.Result.DEFAULT, Event.Result.ALLOW)) {
            var f = new Fixture(1); var event = f.event(); event.setUseInteractedBlock(permission); event.setUseItemInHand(permission);
            f.washer.onIngredientWash(event);
            assertEquals(25, f.purity(f.heldSlot)); assertEquals(Event.Result.DENY, event.useInteractedBlock()); assertEquals(Event.Result.DENY, event.useItemInHand());
        }
    }
    @Test void offhandAndNonRightClickEventsDoNotDoubleWash() {
        var f = new Fixture(1);
        f.washer.onIngredientWash(f.event(Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND));
        f.washer.onIngredientWash(f.event(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND)); f.assertUnchanged(1);
    }
    @Test void malformedIngredientAndDisplayOnlyItemsNeverConsumeWater() {
        var malformed = new Fixture(1);
        ((TestItems.Stack) malformed.storage[malformed.heldSlot]).edit(m -> {
            m.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery", "ingredient_format"), PersistentDataType.INTEGER, 1);
            m.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery", "ingredient_id"), PersistentDataType.STRING, "custom:missing");
            m.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery", "ingredient_purity"), PersistentDataType.INTEGER, 0);
        });
        malformed.washer.onIngredientWash(malformed.event()); malformed.assertUnchanged(1);
        var icon = new Fixture(1); ((TestItems.Stack) icon.storage[icon.heldSlot]).edit(me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems::mark);
        icon.washer.onIngredientWash(icon.event()); icon.assertUnchanged(1);
    }
    @Test void failedInventoryOrWaterUpdatesRollBackBothResources() {
        for (boolean inventoryFailure : List.of(false, true)) {
            var f = new Fixture(1); f.level = 1;
            f.failInventory = inventoryFailure; f.failWater = !inventoryFailure;
            assertThrows(IllegalStateException.class, () -> f.washer.onIngredientWash(f.event()));
            assertEquals(1, f.potatoCount()); assertEquals(0, f.purity(f.heldSlot));
            assertEquals(1, f.level); assertEquals(Material.WATER_CAULDRON, f.material); assertEquals(0, f.sounds); assertTrue(f.messages.isEmpty());
        }
    }
    @Test void deliveryPreservesUnrelatedCanonicalMetadataAndDoesNotMutateSourceStacks() {
        var f = new Fixture(1); var original = (TestItems.Stack) f.storage[f.heldSlot];
        var key = new NamespacedKey("example", "custom"); original.edit(m -> m.getPersistentDataContainer().set(key, PersistentDataType.STRING, "keep"));
        f.washer.onIngredientWash(f.event());
        assertEquals("keep", f.storage[f.heldSlot].getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
        assertEquals(0, IngredientPurityEngine.getPurity(f.items, original)); assertEquals(25, f.purity(f.heldSlot));
    }
    @Test void listenerRegistrationRespectsCancellationAtHighPriority() throws Exception {
        var registration = CauldronWasherEngine.class.getMethod("onIngredientWash", PlayerInteractEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.HIGHEST, registration.priority()); assertTrue(registration.ignoreCancelled());
    }
}
