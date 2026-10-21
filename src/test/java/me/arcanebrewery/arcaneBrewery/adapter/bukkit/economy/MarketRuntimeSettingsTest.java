package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketRuntimeSettingsTest {
    @Test
    void emptyConfigUsesConservativeDefaults() {
        var settings = MarketRuntimeSettings.from(new YamlConfiguration());

        assertEquals(0.75, settings.pricing().sellMultiplier());
        assertEquals(1.25, settings.pricing().buyMultiplier());
        assertEquals(0.01, settings.pricing().minimumPrice());
        assertEquals(100_000.0, settings.pricing().maximumPrice());
        assertEquals(30_000, settings.pricing().quoteLifetimeMillis());
    }

    @Test
    void validOverridesAreParsed() {
        var config = new YamlConfiguration();
        config.set("market.sell-multiplier", 0.6);
        config.set("market.buy-multiplier", 1.4);
        config.set("market.minimum-price", 2.5);
        config.set("market.maximum-price", 5000.0);
        config.set("market.quote-lifetime-seconds", 12.5);

        var settings = MarketRuntimeSettings.from(config);

        assertEquals(0.6, settings.pricing().sellMultiplier());
        assertEquals(1.4, settings.pricing().buyMultiplier());
        assertEquals(2.5, settings.pricing().minimumPrice());
        assertEquals(5000.0, settings.pricing().maximumPrice());
        assertEquals(12_500, settings.pricing().quoteLifetimeMillis());
    }

    @Test
    void invalidMarketConfigFailsClosed() {
        var invertedSpread = new YamlConfiguration();
        invertedSpread.set("market.sell-multiplier", 1.5);
        invertedSpread.set("market.buy-multiplier", 1.0);
        assertThrows(IllegalArgumentException.class, () -> MarketRuntimeSettings.from(invertedSpread));

        var negativeMin = new YamlConfiguration();
        negativeMin.set("market.minimum-price", -1);
        assertThrows(IllegalArgumentException.class, () -> MarketRuntimeSettings.from(negativeMin));

        var badLifetime = new YamlConfiguration();
        badLifetime.set("market.quote-lifetime-seconds", 0);
        assertThrows(IllegalArgumentException.class, () -> MarketRuntimeSettings.from(badLifetime));
    }
}
