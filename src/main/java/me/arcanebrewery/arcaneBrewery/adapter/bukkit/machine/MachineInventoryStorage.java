package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.LegacyAlchemyInventoryReader;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineInventoryCodec;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import java.util.*;
import java.util.function.Function;

public final class MachineInventoryStorage {
    public record Loaded(ItemStack[] items,String process,byte[] raw) {
        public Loaded { items=Arrays.stream(items).map(item -> item==null?null:item.clone()).toArray(ItemStack[]::new); raw=raw==null?null:raw.clone(); }
        @Override public ItemStack[] items() { return Arrays.stream(items).map(item -> item==null?null:item.clone()).toArray(ItemStack[]::new); }
        @Override public byte[] raw() { return raw==null?null:raw.clone(); }
    }
    private final String namespace;
    private final String legacyInventory;
    private final String legacyProcess;
    private final MachineInventoryCodec codec = new MachineInventoryCodec();
    private final Function<ItemStack,byte[]> encodeItem;
    private final Function<byte[],ItemStack> decodeItem;
    public MachineInventoryStorage(String namespace,String legacyInventory,String legacyProcess) {
        this(namespace,legacyInventory,legacyProcess,ItemStack::serializeAsBytes,ItemStack::deserializeBytes);
    }
    public MachineInventoryStorage(String namespace,String legacyInventory,String legacyProcess,
            Function<ItemStack,byte[]> encodeItem,Function<byte[],ItemStack> decodeItem) {
        this.namespace = namespace; this.legacyInventory = legacyInventory; this.legacyProcess = legacyProcess;
        this.encodeItem=Objects.requireNonNull(encodeItem); this.decodeItem=Objects.requireNonNull(decodeItem);
    }
    public NamespacedKey key(Block core) {
        return new NamespacedKey(namespace,"machine_inventory_" + (core.getX() & 15) + "_" + core.getY() + "_" + (core.getZ() & 15));
    }
    private NamespacedKey marker(Block core) { return new NamespacedKey(namespace,key(core).getKey().replace("machine_inventory_","machine_inventory_seen_")); }
    private PersistentDataContainer data(Block core) {
        if (!core.getWorld().isChunkLoaded(core.getX() >> 4,core.getZ() >> 4)) throw new IllegalStateException("machine chunk unloaded");
        return core.getChunk().getPersistentDataContainer();
    }
    public Loaded load(Block core) {
        var data = data(core);
        if (data.has(marker(core)) && !data.has(key(core))) throw new IllegalArgumentException("committed machine inventory is missing; legacy backup retained");
        if (data.has(key(core))) {
            byte[] raw = data.get(key(core),PersistentDataType.BYTE_ARRAY);
            if (raw == null) throw new IllegalArgumentException("invalid machine inventory type");
            var snapshot = codec.decode(raw);
            var slots = snapshot.slots(); var items = new ItemStack[9];
            for (int i = 0; i < 9; i++) if (slots.get(i).length > 0) {
                items[i] = Objects.requireNonNull(decodeItem.apply(slots.get(i)),"invalid native machine item");
                if (air(items[i]) || items[i].getAmount()<=0) throw new IllegalArgumentException("invalid native machine item");
            }
            return new Loaded(items,snapshot.process(),raw);
        }
        ItemStack[] items = new ItemStack[9]; String process = "";
        if (core.getState() instanceof PersistentDataHolder holder) {
            var old = holder.getPersistentDataContainer();
            var key = new NamespacedKey(namespace,legacyInventory);
            if (old.has(key)) {
                String encoded = old.get(key,PersistentDataType.STRING);
                if (encoded == null) throw new IllegalArgumentException("invalid legacy machine inventory");
                items = new LegacyAlchemyInventoryReader().read(encoded,9);
            }
            if (legacyProcess != null && old.has(new NamespacedKey(namespace,legacyProcess))) {
                process = old.get(new NamespacedKey(namespace,legacyProcess),PersistentDataType.STRING);
                if (process == null || process.isBlank()) throw new IllegalArgumentException("invalid legacy process");
            }
        }
        return new Loaded(items,process,null);
    }
    public byte[] save(Block core,byte[] expected,ItemStack[] items,String process) {
        if (items.length != 9) throw new IllegalArgumentException("invalid inventory size");
        var data = data(core);
        byte[] previous = data.get(key(core),PersistentDataType.BYTE_ARRAY);
        if ((data.has(marker(core)) && !data.has(key(core))) || (data.has(key(core)) && previous==null)
                || !Arrays.equals(expected,previous)) throw new IllegalStateException("machine inventory changed");
        var payloads = Arrays.stream(items).map(item -> item == null || air(item) ? new byte[0] : encodeItem.apply(item)).toList();
        byte[] raw = codec.encode(new MachineInventoryCodec.Snapshot(process,payloads));
        data.set(key(core),PersistentDataType.BYTE_ARRAY,raw.clone());
        data.set(marker(core),PersistentDataType.INTEGER,1);
        return raw.clone();
    }
    public void remove(Block core) { var data=data(core); data.remove(key(core)); data.remove(marker(core)); }
    private boolean air(ItemStack item) {
        return item.getType()==org.bukkit.Material.AIR || item.getType()==org.bukkit.Material.CAVE_AIR || item.getType()==org.bukkit.Material.VOID_AIR;
    }
}
