package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyPreparation;
import me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyPreparationCodec;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;

public final class AlchemyPreparationMetadata {
    public static final int VERSION = 2;
    private final String namespace;
    private final AlchemyPreparationCodec codec = new AlchemyPreparationCodec();

    public AlchemyPreparationMetadata(JavaPlugin plugin) {
        this.namespace = plugin.getName().toLowerCase(java.util.Locale.ROOT);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public void write(ItemMeta meta, AlchemyPreparation preparation) {
        var pdc = meta.getPersistentDataContainer();
        pdc.set(key("alchemy_prep_format"), PersistentDataType.INTEGER, VERSION);
        pdc.set(key("alchemy_preparation"), PersistentDataType.STRING, codec.encodeBase64(preparation));
        pdc.remove(key("alchemy_additive_id"));
        pdc.remove(key("alchemy_additive_dose"));
    }

    public Optional<AlchemyPreparation> read(ItemMeta meta) {
        var pdc = meta.getPersistentDataContainer();
        Integer version = pdc.get(key("alchemy_prep_format"), PersistentDataType.INTEGER);
        if (version == null) return Optional.empty();

        if (version == 1) {
            String id = pdc.get(key("alchemy_additive_id"), PersistentDataType.STRING);
            Double dose = pdc.get(key("alchemy_additive_dose"), PersistentDataType.DOUBLE);
            if (id == null || dose == null)
                throw new IllegalArgumentException("incomplete legacy alchemy preparation metadata");
            return Optional.of(new AlchemyPreparation(Map.of(id, dose)));
        }

        if (version != VERSION) throw new IllegalArgumentException("unsupported alchemy preparation version");
        String payload = pdc.get(key("alchemy_preparation"), PersistentDataType.STRING);
        if (payload == null) throw new IllegalArgumentException("missing alchemy preparation payload");
        return Optional.of(codec.decodeBase64(payload));
    }
}
