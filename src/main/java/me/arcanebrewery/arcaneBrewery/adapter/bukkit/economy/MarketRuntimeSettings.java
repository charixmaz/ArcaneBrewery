package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.application.economy.MarketPricingProfile;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Objects;

public record MarketRuntimeSettings(MarketPricingProfile pricing) {
    public MarketRuntimeSettings {
        Objects.requireNonNull(pricing);
    }

    public static MarketRuntimeSettings from(FileConfiguration config) {
        Objects.requireNonNull(config);
        double quoteSeconds = number(config, "market.quote-lifetime-seconds", 30.0);
        if (quoteSeconds <= 0 || quoteSeconds > 3600)
            throw new IllegalArgumentException("market.quote-lifetime-seconds must be within 0..3600");
        long quoteMillis = Math.round(quoteSeconds * 1000.0);

        return new MarketRuntimeSettings(new MarketPricingProfile(
                number(config, "market.sell-multiplier", 0.75),
                number(config, "market.buy-multiplier", 1.25),
                number(config, "market.minimum-price", 0.01),
                number(config, "market.maximum-price", 100_000.0),
                quoteMillis
        ));
    }

    private static double number(FileConfiguration config, String path, double fallback) {
        Object raw = config.get(path);
        if (raw == null) return fallback;
        if (!(raw instanceof Number number))
            throw new IllegalArgumentException(path + " must be numeric");
        double value = number.doubleValue();
        if (!Double.isFinite(value))
            throw new IllegalArgumentException(path + " must be finite");
        return value;
    }
}
