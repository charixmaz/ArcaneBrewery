package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.application.aging.AgingBatchState;
import me.arcanebrewery.arcaneBrewery.application.aging.AgingSettings;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AgingBatchMetadata {
    public static final int VERSION = 1;
    private final String namespace;

    public AgingBatchMetadata(JavaPlugin plugin) {
        this("arcanebrewery");
    }

    public AgingBatchMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
        key("aging_format");
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public Optional<AgingBatchState> read(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        Integer version = data.get(key("aging_format"), PersistentDataType.INTEGER);
        if (version == null) return Optional.empty();
        if (version != VERSION) throw new IllegalArgumentException("unsupported aging metadata version");
        try {
            UUID batchId = UUID.fromString(required(data, "aging_batch_id", PersistentDataType.STRING));
            String wood = required(data, "aging_wood", PersistentDataType.STRING);
            long started = required(data, "aging_started_at", PersistentDataType.LONG);
            double oxidation = required(data, "aging_oxidation", PersistentDataType.DOUBLE);
            double temperature = required(data, "aging_storage_temperature", PersistentDataType.DOUBLE);
            double cleanliness = required(data, "aging_cleanliness", PersistentDataType.DOUBLE);
            return Optional.of(new AgingBatchState(VERSION, batchId,
                    new AgingSettings(wood, oxidation, temperature, cleanliness), started,
                    data.getOrDefault(key("aging_maturation_seconds"), PersistentDataType.LONG,120L),
                    data.getOrDefault(key("aging_recipe_timing_v1"),PersistentDataType.BYTE,(byte)0)==1));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("malformed aging metadata", failure);
        }
    }

    public void write(ItemMeta meta, AgingBatchState state) {
        var data = meta.getPersistentDataContainer();
        data.set(key("aging_format"), PersistentDataType.INTEGER, VERSION);
        data.set(key("aging_batch_id"), PersistentDataType.STRING, state.batchId().toString());
        data.set(key("aging_wood"), PersistentDataType.STRING, state.settings().woodType());
        data.set(key("aging_started_at"), PersistentDataType.LONG, state.startedAtEpochMillis());
        data.set(key("aging_recipe_timing_v1"),PersistentDataType.BYTE,(byte)(state.recipeTiming()?1:0));
        data.set(key("aging_maturation_seconds"), PersistentDataType.LONG, state.maturationSeconds());
        data.set(key("aging_oxidation"), PersistentDataType.DOUBLE, state.settings().oxidation());
        data.set(key("aging_storage_temperature"), PersistentDataType.DOUBLE, state.settings().storageTemperatureCelsius());
        data.set(key("aging_cleanliness"), PersistentDataType.DOUBLE, state.settings().caskCleanliness());
    }

    private <P,C> C required(org.bukkit.persistence.PersistentDataContainer data, String name,
            org.bukkit.persistence.PersistentDataType<P,C> type) {
        var key = key(name);
        if (!data.has(key, type)) throw new IllegalArgumentException("missing aging field " + name);
        return data.get(key, type);
    }
}
