package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import java.util.*;

/** Atomic TileState snapshot update; failures propagate and must lock the GUI, never reset it to empty. */
public final class AlchemyMachineStorage {
    private static final List<String> LEGACY = List.of("alchemy_machine_format", "alchemy_machine_batch",
            "alchemy_machine_started", "alchemy_machine_duration", "alchemy_machine_status", "alchemy_machine_inventory");
    public record Loaded(AlchemyMachineSnapshot snapshot, byte[] persisted) {
        public Loaded { persisted = persisted == null ? null : persisted.clone(); }
        @Override public byte[] persisted() { return persisted == null ? null : persisted.clone(); }
    }
    private final String namespace;
    private final AlchemyMachineSnapshotCodec codec = new AlchemyMachineSnapshotCodec();
    private final AlchemyMachineItems items;
    public AlchemyMachineStorage(String namespace, AlchemyMachineItems items) { this.namespace = namespace; this.items = items; }
    private NamespacedKey key(String value) { return new NamespacedKey(namespace, value); }
    public boolean hasData(Block core) {
        var data = tile(core).getPersistentDataContainer();
        return data.has(key("alchemy_machine_snapshot")) || LEGACY.stream().anyMatch(k -> data.has(key(k)));
    }
    public Loaded load(Block core) {
        var data = tile(core).getPersistentDataContainer();
        if (data.has(key("alchemy_machine_snapshot"))) {
            byte[] raw = data.get(key("alchemy_machine_snapshot"), PersistentDataType.BYTE_ARRAY);
            if (raw == null) throw new IllegalArgumentException("invalid machine snapshot type");
            return new Loaded(codec.decode(raw), raw);
        }
        if (LEGACY.stream().noneMatch(k -> data.has(key(k))))
            return new Loaded(new AlchemyMachineSnapshot(Optional.empty(), List.of(new byte[0], new byte[0], new byte[0])), null);
        String encoded = data.get(key("alchemy_machine_inventory"), PersistentDataType.STRING);
        if (encoded == null) throw new IllegalArgumentException("missing legacy inventory");
        ItemStack[] inventory = new LegacyAlchemyInventoryReader().read(encoded);
        Optional<AlchemyMachineOperation> operation = Optional.empty();
        if (LEGACY.subList(0, 5).stream().anyMatch(k -> data.has(key(k)))) {
            Integer format = data.get(key("alchemy_machine_format"), PersistentDataType.INTEGER);
            String batch = data.get(key("alchemy_machine_batch"), PersistentDataType.STRING);
            Long start = data.get(key("alchemy_machine_started"), PersistentDataType.LONG);
            Long duration = data.get(key("alchemy_machine_duration"), PersistentDataType.LONG);
            String status = data.get(key("alchemy_machine_status"), PersistentDataType.STRING);
            if (format == null || batch == null || start == null || duration == null || status == null)
                throw new IllegalArgumentException("incomplete legacy operation");
            var state = new AlchemyMachineState(format, UUID.fromString(batch), start, duration, AlchemyMachineState.Status.valueOf(status));
            if (state.status() == AlchemyMachineState.Status.COMPLETED)
                return new Loaded(new AlchemyMachineSnapshot(Optional.empty(), items.encode(inventory)), null);
            if (AlchemyMachineItems.empty(inventory[0]) || inventory[0].getAmount() != 1)
                throw new IllegalArgumentException("ambiguous legacy base");
            var meta = Objects.requireNonNull(inventory[0].getItemMeta());
            var fermented = new FermentationBatchMetadata(namespace).read(meta).orElseThrow();
            if (fermented.status() != FermentationBatchState.Status.COMPLETED) throw new IllegalArgumentException("invalid legacy base");
            var prepared = new AlchemyPreparationMetadata(namespace).read(meta);
            AlchemyPreparation plan;
            if (prepared.isPresent()) {
                // Old completion persisted prepared output/consumed catalysts before marking COMPLETED.
                state = state.complete(System.currentTimeMillis());
                plan = prepared.orElseThrow();
            } else {
                if (state.status() == AlchemyMachineState.Status.COMPLETED)
                    throw new IllegalArgumentException("legacy completion has no prepared output");
                plan = new AlchemyMachineService().combine(AlchemyMachineInputs.catalysts(inventory));
            }
            operation = Optional.of(new AlchemyMachineOperation(state, fermented.batchId(), plan));
        }
        return new Loaded(new AlchemyMachineSnapshot(operation, items.encode(inventory)), null);
    }
    public byte[] save(Block core, byte[] expected, AlchemyMachineSnapshot snapshot) {
        byte[] encoded = codec.encode(snapshot);
        var state = tile(core);
        var data = state.getPersistentDataContainer();
        byte[] current = data.get(key("alchemy_machine_snapshot"), PersistentDataType.BYTE_ARRAY);
        if (data.has(key("alchemy_machine_snapshot")) && current == null || !Arrays.equals(expected, current))
            throw new IllegalStateException("machine snapshot changed");
        data.set(key("alchemy_machine_snapshot"), PersistentDataType.BYTE_ARRAY, encoded);
        LEGACY.forEach(k -> data.remove(key(k)));
        if (!state.update(false, false)) throw new IllegalStateException("machine persistence failed");
        return encoded;
    }
    private TileState tile(Block core) {
        if (!core.getWorld().isChunkLoaded(core.getX() >> 4, core.getZ() >> 4)
                || core.getType() != Material.ENCHANTING_TABLE || !(core.getState() instanceof TileState state))
            throw new IllegalStateException("machine core is unavailable");
        return state;
    }
}
