package me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation;

import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FermentationBatchMetadata {
    public static final int VERSION = 1;
    private final String namespace;
    private final FermentationStateCodec codec = new FermentationStateCodec();

    public FermentationBatchMetadata(JavaPlugin plugin) {
        this("arcanebrewery");
    }

    public FermentationBatchMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
        key("fermentation_format");
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(namespace, value);
    }

    public void write(ItemMeta meta, FermentationBatchState state) {
        var data = meta.getPersistentDataContainer();
        data.set(key("fermentation_format"), PersistentDataType.INTEGER, VERSION);
        data.set(key("fermentation_batch_id"), PersistentDataType.STRING, state.batchId().toString());
        data.set(key("fermentation_stage"), PersistentDataType.STRING,
                state.status() == FermentationBatchState.Status.COMPLETED ? "FERMENTED_BASE" : "FERMENTING");
        data.set(key("fermentation_state"), PersistentDataType.STRING, codec.encodeBase64(state));
    }

    public Optional<FermentationBatchState> read(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        Integer version = data.get(key("fermentation_format"), PersistentDataType.INTEGER);
        String payload = data.get(key("fermentation_state"), PersistentDataType.STRING);
        if (version == null && payload == null) return Optional.empty();
        if (version == null || version != VERSION || payload == null)
            throw new IllegalArgumentException("incomplete fermentation metadata");
        var state = codec.decodeBase64(payload);
        String batch = data.get(key("fermentation_batch_id"), PersistentDataType.STRING);
        if (batch == null || !UUID.fromString(batch).equals(state.batchId()))
            throw new IllegalArgumentException("fermentation batch id mismatch");
        return Optional.of(state);
    }

    public String stage(ItemMeta meta) {
        return Optional.ofNullable(meta.getPersistentDataContainer()
                .get(key("fermentation_stage"), PersistentDataType.STRING)).orElse("");
    }
}
