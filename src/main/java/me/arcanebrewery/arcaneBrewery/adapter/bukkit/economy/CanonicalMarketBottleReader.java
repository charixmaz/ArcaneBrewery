package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.application.economy.MarketAsset;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class CanonicalMarketBottleReader {
    private final String namespace;
    private final FinalDrinkMetadata drinks;

    public CanonicalMarketBottleReader(String namespace) {
        this.namespace = Objects.requireNonNull(namespace).toLowerCase(Locale.ROOT);
        this.drinks = new FinalDrinkMetadata(this.namespace);
    }

    public Optional<MarketAsset> read(ItemStack item) {
        if (item == null || item.getType() != Material.POTION || item.getAmount() != 1 || !item.hasItemMeta()) {
            return Optional.empty();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        var data = meta.getPersistentDataContainer();

        Integer format = data.get(key("drink_format"), PersistentDataType.INTEGER);
        String stage = data.get(key("drink_stage"), PersistentDataType.STRING);
        String fingerprint = data.get(key("drink_fingerprint"), PersistentDataType.STRING);
        if (format == null || format != FinalDrinkMetadata.VERSION
                || !"FINISHED".equals(stage)
                || fingerprint == null
                || !fingerprint.matches("[0-9a-f]{64}")) {
            return Optional.empty();
        }

        var value = drinks.readValue(meta);
        if (value.isEmpty() || drinks.readConsumption(meta).isEmpty() || drinks.readAlchemy(meta).isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(MarketAsset.from(fingerprint, value.get()));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }
}
