package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyMachineStorageTest {
    private final AlchemyMachineItems items = new AlchemyMachineItems(
            item -> new byte[]{(byte) item.getAmount()}, bytes -> TestItems.stack(Material.WHEAT, bytes[0]));
    private final AlchemyMachineStorage storage = new AlchemyMachineStorage("arcanebrewery", items);
    private AlchemyMachineSnapshot snapshot(int count) {
        return new AlchemyMachineSnapshot(Optional.empty(), List.of(new byte[]{(byte)count}, new byte[0], new byte[0]));
    }

    @Test void enchantingTableIsAPersistentTileOnTargetApi() {
        assertTrue(TileState.class.isAssignableFrom(EnchantingTable.class));
    }
    @Test void snapshotAndAllThreeSlotsPersistTogetherAndReopenExactly() {
        var core = new Core();
        var loaded = storage.load(core.block);
        assertFalse(storage.hasData(core.block));
        assertTrue(loaded.snapshot().operation().isEmpty());
        byte[] saved = storage.save(core.block, null, snapshot(5));
        var reopened = storage.load(core.block);
        assertArrayEquals(saved, reopened.persisted());
        assertEquals(5, items.decode(reopened.snapshot().items())[0].getAmount());
        assertTrue(storage.hasData(core.block));
        assertEquals("kept", core.persisted.getPersistentDataContainer().get(new NamespacedKey("other","data"), PersistentDataType.STRING));
        assertArrayEquals(new Object[]{false,false}, core.lastUpdate);
    }
    @Test void failedTileUpdateLeavesOriginalSnapshotIntact() {
        var core = new Core();
        byte[] original = storage.save(core.block, null, snapshot(3));
        core.allowUpdate = false;
        assertThrows(IllegalStateException.class, () -> storage.save(core.block, original, snapshot(1)));
        assertArrayEquals(original, storage.load(core.block).persisted());
        assertEquals(3, items.decode(storage.load(core.block).snapshot().items())[0].getAmount());
    }
    @Test void staleSessionCannotOverwriteCommittedMachineData() {
        var core = new Core();
        byte[] original = storage.save(core.block, null, snapshot(3));
        byte[] committed = storage.save(core.block, original, snapshot(2));
        assertThrows(IllegalStateException.class, () -> storage.save(core.block, original, snapshot(3)));
        assertArrayEquals(committed, storage.load(core.block).persisted());
    }
    @Test void malformedSnapshotAndPartialLegacyStateFailClosedWithoutBeingErased() {
        var core = new Core();
        var key = new NamespacedKey("arcanebrewery","alchemy_machine_snapshot");
        core.persisted.getPersistentDataContainer().set(key, PersistentDataType.BYTE_ARRAY,new byte[]{1,2});
        assertThrows(IllegalArgumentException.class, () -> storage.load(core.block));
        assertTrue(core.persisted.getPersistentDataContainer().has(key));
        core.persisted.getPersistentDataContainer().remove(key);
        core.persisted.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","alchemy_machine_format"),PersistentDataType.INTEGER,1);
        assertThrows(IllegalArgumentException.class, () -> storage.load(core.block));
        assertTrue(storage.hasData(core.block));
    }
    @Test void itemFramingRejectsWrongCountsAndInvalidNativeStackAmounts() {
        assertThrows(IllegalArgumentException.class, () -> items.decode(List.of(new byte[]{1})));
        assertThrows(IllegalArgumentException.class, () -> items.decode(List.of(new byte[]{65},new byte[0],new byte[0])));
    }


    @Test void machineConditionPersistsAtomicallyWithInventory() {
        var core = new Core();
        var conditioned = new AlchemyMachineSnapshot(
                Optional.empty(),
                new AlchemyMachineCondition(42.5, 61.0),
                List.of(new byte[]{3}, new byte[0], new byte[0]));
        byte[] saved = storage.save(core.block, null, conditioned);
        var reopened = storage.load(core.block);
        assertArrayEquals(saved, reopened.persisted());
        assertEquals(new AlchemyMachineCondition(42.5, 61.0), reopened.snapshot().condition());
        assertEquals(3, items.decode(reopened.snapshot().items())[0].getAmount());
    }
    private static final class Core {
        ItemMeta persisted = TestItems.stack(Material.PAPER,1).getItemMeta();
        boolean allowUpdate = true;
        Object[] lastUpdate;
        final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},
                (p,m,a) -> switch(m.getName()) {
                    case "isChunkLoaded" -> true;
                    case "getUID" -> new UUID(0,1);
                    case "getName" -> "test";
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        final Block block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},
                (p,m,a) -> switch(m.getName()) {
                    case "getWorld" -> world;
                    case "getX","getZ" -> 0;
                    case "getY" -> 64;
                    case "getType" -> Material.ENCHANTING_TABLE;
                    case "getState" -> tile();
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        Core() { persisted.getPersistentDataContainer().set(new NamespacedKey("other","data"),PersistentDataType.STRING,"kept"); }
        private EnchantingTable tile() {
            ItemMeta snapshot = persisted.clone();
            return (EnchantingTable) Proxy.newProxyInstance(EnchantingTable.class.getClassLoader(),new Class<?>[]{EnchantingTable.class},
                    (p,m,a) -> switch(m.getName()) {
                        case "getPersistentDataContainer" -> snapshot.getPersistentDataContainer();
                        case "update" -> {
                            lastUpdate = a;
                            if (allowUpdate) persisted = snapshot.clone();
                            yield allowUpdate;
                        }
                        default -> throw new UnsupportedOperationException(m.getName());
                    });
        }
    }
}
