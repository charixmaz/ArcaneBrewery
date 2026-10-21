package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.application.aging.AgingResult;
import me.arcanebrewery.arcaneBrewery.application.consumption.DrinkConsumptionProfile;
import me.arcanebrewery.arcaneBrewery.application.consumption.DrinkStrengthCalculator;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class FinalDrinkMetadata {
    public static final int VERSION = 2;
    private final String namespace;
    private final DrinkStrengthCalculator strength = new DrinkStrengthCalculator();

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
        data.set(key("drink_magical_potency"), PersistentDataType.DOUBLE, result.simulation().quality().magicalPotency());
        data.set(key("drink_strength"), PersistentDataType.DOUBLE, strength.calculate(result.process()));
        data.set(key("drink_stage"), PersistentDataType.STRING, "FINISHED");
    }

    public Optional<DrinkConsumptionProfile> readConsumption(ItemMeta meta) {
        var data = meta.getPersistentDataContainer();
        Integer version = data.get(key("drink_format"), PersistentDataType.INTEGER);
        String stage = data.get(key("drink_stage"), PersistentDataType.STRING);
        if (version == null || stage == null) return Optional.empty();
        if (version != VERSION || !"FINISHED".equals(stage)) return Optional.empty();

        Double drinkStrength = data.get(key("drink_strength"), PersistentDataType.DOUBLE);
        Double quality = data.get(key("drink_quality"), PersistentDataType.DOUBLE);
        Double contamination = data.get(key("drink_contamination"), PersistentDataType.DOUBLE);
        Double magicalPotency = data.get(key("drink_magical_potency"), PersistentDataType.DOUBLE);
        String fingerprint = data.get(key("drink_fingerprint"), PersistentDataType.STRING);

        if (drinkStrength == null || quality == null || contamination == null
                || magicalPotency == null || fingerprint == null || fingerprint.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new DrinkConsumptionProfile(
                    drinkStrength, quality, contamination, magicalPotency));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }
}
