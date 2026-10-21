package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlchemyRuntimeSettingsTest {
    @Test
    void emptyConfigUsesBehaviorPreservingDefaults() {
        var settings = AlchemyRuntimeSettings.from(new YamlConfiguration());

        assertEquals(8_000, settings.balance().processingDurationMillis());
        assertEquals(55.0, settings.balance().unstableThreshold());
        assertEquals(Material.IRON_INGOT, settings.repairMaterial());
        assertEquals(Material.SPONGE, settings.cleaningMaterial());
    }

    @Test
    void validOverridesAreParsed() {
        var config = new YamlConfiguration();
        config.set("alchemy.processing-seconds", 12.5);
        config.set("alchemy.thresholds.unstable", 50.0);
        config.set("alchemy.thresholds.failed", 70.0);
        config.set("alchemy.thresholds.critical", 88.0);
        config.set("alchemy.maintenance.repair-material", "COPPER_INGOT");
        config.set("alchemy.maintenance.cleaning-material", "WET_SPONGE");
        config.set("alchemy.maintenance.repair-integrity", 33.0);
        config.set("alchemy.maintenance.cleaning-amount", 44.0);
        config.set("alchemy.critical-explosion-power", 2.0);

        var settings = AlchemyRuntimeSettings.from(config);

        assertEquals(12_500, settings.balance().processingDurationMillis());
        assertEquals(50.0, settings.balance().unstableThreshold());
        assertEquals(33.0, settings.balance().repairIntegrity());
        assertEquals(44.0, settings.balance().cleaningAmount());
        assertEquals(2.0f, settings.balance().criticalExplosionPower());
        assertEquals(Material.COPPER_INGOT, settings.repairMaterial());
        assertEquals(Material.WET_SPONGE, settings.cleaningMaterial());
    }

    @Test
    void malformedConfigFailsClosed() {
        var reversed = new YamlConfiguration();
        reversed.set("alchemy.thresholds.unstable", 80);
        reversed.set("alchemy.thresholds.failed", 70);
        assertThrows(IllegalArgumentException.class, () -> AlchemyRuntimeSettings.from(reversed));

        var invalidMaterial = new YamlConfiguration();
        invalidMaterial.set("alchemy.maintenance.repair-material", "NOT_A_REAL_ITEM");
        assertThrows(IllegalArgumentException.class, () -> AlchemyRuntimeSettings.from(invalidMaterial));

        var sameMaterial = new YamlConfiguration();
        sameMaterial.set("alchemy.maintenance.repair-material", "SPONGE");
        sameMaterial.set("alchemy.maintenance.cleaning-material", "SPONGE");
        assertThrows(IllegalArgumentException.class, () -> AlchemyRuntimeSettings.from(sameMaterial));
    }
}
