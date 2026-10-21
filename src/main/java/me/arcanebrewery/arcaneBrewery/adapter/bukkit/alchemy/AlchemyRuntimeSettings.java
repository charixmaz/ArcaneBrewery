package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyBalanceProfile;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

public record AlchemyRuntimeSettings(
        AlchemyBalanceProfile balance,
        Material repairMaterial,
        Material cleaningMaterial
) {
    public AlchemyRuntimeSettings {
        Objects.requireNonNull(balance);
        Objects.requireNonNull(repairMaterial);
        Objects.requireNonNull(cleaningMaterial);
        if (!repairMaterial.isItem() || !cleaningMaterial.isItem())
            throw new IllegalArgumentException("maintenance materials must be items");
        if (repairMaterial == cleaningMaterial)
            throw new IllegalArgumentException("repair and cleaning materials must differ");
    }

    public static AlchemyRuntimeSettings from(FileConfiguration config) {
        Objects.requireNonNull(config);
        double seconds = number(config, "alchemy.processing-seconds", 8.0);
        long durationMillis;
        try {
            durationMillis = Math.round(Math.multiplyExact((long) Math.ceil(seconds * 1000.0), 1L));
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("alchemy.processing-seconds is too large", overflow);
        }
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 3600)
            throw new IllegalArgumentException("alchemy.processing-seconds must be within 0..3600");

        var balance = new AlchemyBalanceProfile(
                durationMillis,
                number(config, "alchemy.thresholds.unstable", 55.0),
                number(config, "alchemy.thresholds.failed", 75.0),
                number(config, "alchemy.thresholds.critical", 90.0),
                number(config, "alchemy.weights.additive-load", 18.0),
                number(config, "alchemy.weights.diversity", 16.0),
                number(config, "alchemy.weights.integrity-loss", 0.45),
                number(config, "alchemy.weights.contamination", 0.35),
                number(config, "alchemy.maintenance.repair-integrity", 20.0),
                number(config, "alchemy.maintenance.cleaning-amount", 25.0),
                (float) number(config, "alchemy.critical-explosion-power", 1.5)
        );

        return new AlchemyRuntimeSettings(
                balance,
                material(config, "alchemy.maintenance.repair-material", Material.IRON_INGOT),
                material(config, "alchemy.maintenance.cleaning-material", Material.SPONGE)
        );
    }

    private static double number(FileConfiguration config, String path, double fallback) {
        Object raw = config.get(path);
        if (raw == null) return fallback;
        if (!(raw instanceof Number number))
            throw new IllegalArgumentException(path + " must be numeric");
        double value = number.doubleValue();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(path + " must be finite");
        return value;
    }

    private static Material material(FileConfiguration config, String path, Material fallback) {
        String raw = config.getString(path);
        if (raw == null || raw.isBlank()) return fallback;
        Material material = Material.matchMaterial(raw);
        if (material == null) throw new IllegalArgumentException(path + " is not a valid Material: " + raw);
        return material;
    }
}
