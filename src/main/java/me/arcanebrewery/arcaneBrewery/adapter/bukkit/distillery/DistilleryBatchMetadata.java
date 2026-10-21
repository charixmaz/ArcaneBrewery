package me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery;

import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DistilleryBatchMetadata {
    public static final int VERSION = 1;
    private final String namespace;
    private final DistilleryStateCodec codec = new DistilleryStateCodec();

    public DistilleryBatchMetadata(JavaPlugin plugin) {
        this(plugin.getName().toLowerCase(java.util.Locale.ROOT));
    }

    public DistilleryBatchMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
        key("distillery_format");
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(namespace, value);
    }

    public void write(ItemMeta meta, DistilleryBatchState state, String stage) {
        var data = meta.getPersistentDataContainer();
        data.set(key("distillery_format"), PersistentDataType.INTEGER, VERSION);
        data.set(key("distillery_batch_id"), PersistentDataType.STRING, state.batchId().toString());
        data.set(key("distillery_stage"), PersistentDataType.STRING, stage);
        data.set(key("distillery_state"), PersistentDataType.STRING, codec.encodeBase64(state));
    }

    public Optional<DistilleryBatchState> read(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        Integer version = data.get(key("distillery_format"), PersistentDataType.INTEGER);
        String payload = data.get(key("distillery_state"), PersistentDataType.STRING);
        if (version == null && payload == null) return Optional.empty();
        if (version == null || version != VERSION || payload == null)
            throw new IllegalArgumentException("incomplete distillery metadata");
        var state = codec.decodeBase64(payload);
        String batch = data.get(key("distillery_batch_id"), PersistentDataType.STRING);
        if (batch == null || !UUID.fromString(batch).equals(state.batchId()))
            throw new IllegalArgumentException("distillery batch id mismatch");
        return Optional.of(state);
    }

    public String stage(ItemMeta meta) {
        return Optional.ofNullable(meta.getPersistentDataContainer()
                .get(key("distillery_stage"), PersistentDataType.STRING)).orElse("");
    }
}
