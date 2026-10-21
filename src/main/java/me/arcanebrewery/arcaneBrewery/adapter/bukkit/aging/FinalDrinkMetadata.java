package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.application.aging.AgingResult;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

public final class FinalDrinkMetadata {
    public static final int VERSION = 1;
    private final String namespace;

    public FinalDrinkMetadata(JavaPlugin plugin) {
        this(plugin.getName().toLowerCase(Locale.ROOT));
    }

    public FinalDrinkMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public void write(ItemMeta meta, AgingResult result) {
        var data = meta.getPersistentDataContainer();
        data.set(key("drink_format"), PersistentDataType.INTEGER, VERSION);
        data.set(key("drink_fingerprint"), PersistentDataType.STRING, result.simulation().fingerprint().value());
        data.set(key("drink_quality"), PersistentDataType.DOUBLE, result.simulation().quality().overallScore());
        data.set(key("drink_contamination"), PersistentDataType.DOUBLE, result.simulation().quality().contamination());
        data.set(key("drink_stage"), PersistentDataType.STRING, "FINISHED");
    }
}
