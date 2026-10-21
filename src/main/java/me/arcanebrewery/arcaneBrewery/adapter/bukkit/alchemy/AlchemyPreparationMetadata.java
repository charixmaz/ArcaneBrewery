package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyPreparation;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;

public final class AlchemyPreparationMetadata {
    public static final int VERSION = 1;
    private final String namespace;

    public AlchemyPreparationMetadata(JavaPlugin plugin) {
        this.namespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public void write(ItemMeta meta, AlchemyPreparation preparation) {
        if (preparation.additives().size() != 1)
            throw new IllegalArgumentException("V1.14 item metadata supports exactly one additive");
        var entry = preparation.additives().entrySet().iterator().next();
        var pdc = meta.getPersistentDataContainer();
        pdc.set(key("alchemy_prep_format"), PersistentDataType.INTEGER, VERSION);
        pdc.set(key("alchemy_additive_id"), PersistentDataType.STRING, entry.getKey());
        pdc.set(key("alchemy_additive_dose"), PersistentDataType.DOUBLE, entry.getValue());
    }

    public Optional<AlchemyPreparation> read(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        Integer version = pdc.get(key("alchemy_prep_format"), PersistentDataType.INTEGER);
        if (version == null) return Optional.empty();
        if (version != VERSION) throw new IllegalArgumentException("unsupported alchemy preparation version");
        String id = pdc.get(key("alchemy_additive_id"), PersistentDataType.STRING);
        Double dose = pdc.get(key("alchemy_additive_dose"), PersistentDataType.DOUBLE);
        if (id == null || dose == null) throw new IllegalArgumentException("incomplete alchemy preparation metadata");
        return Optional.of(new AlchemyPreparation(Map.of(id, dose)));
    }
}
