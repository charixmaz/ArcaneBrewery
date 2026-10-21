package me.arcanebrewery.arcaneeconomy.adapter.bukkit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsLoaderTest {
    @TempDir Path temp;

    @Test
    void emptyConfigUsesSafeDefaults() {
        var settings = SettingsLoader.load(new YamlConfiguration(), temp);

        assertEquals(2, settings.money().scale());
        assertEquals(0, settings.money().startingMinor());
        assertEquals(temp.resolve("economy.db"), settings.sqliteFile());
        assertEquals(5000, settings.busyTimeoutMillis());
        assertEquals("$", settings.symbol());
    }

    @Test
    void customCurrencySettingsAreParsedExactly() {
        var config = new YamlConfiguration();
        config.set("currency.name-singular", "crown");
        config.set("currency.name-plural", "crowns");
        config.set("currency.symbol", "C");
        config.set("currency.scale", 3);
        config.set("currency.starting-balance", "12.345");
        config.set("currency.minimum-balance", "-50.000");
        config.set("currency.maximum-balance", "9999.999");
        config.set("storage.sqlite-file", "money.sqlite");
        config.set("storage.busy-timeout-ms", 2500);

        var settings = SettingsLoader.load(config, temp);

        assertEquals(3, settings.money().scale());
        assertEquals(12345, settings.money().startingMinor());
        assertEquals(-50000, settings.money().minimumMinor());
        assertEquals(9999999, settings.money().maximumMinor());
        assertEquals(temp.resolve("money.sqlite"), settings.sqliteFile());
        assertEquals(2500, settings.busyTimeoutMillis());
    }

    @Test
    void pathTraversalAndInvalidMoneyConfigFailClosed() {
        var traversal = new YamlConfiguration();
        traversal.set("storage.sqlite-file", "../money.db");
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.load(traversal, temp));

        var badScale = new YamlConfiguration();
        badScale.set("currency.scale", 9);
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.load(badScale, temp));

        var badStarting = new YamlConfiguration();
        badStarting.set("currency.starting-balance", "100.00");
        badStarting.set("currency.maximum-balance", "10.00");
        assertThrows(IllegalArgumentException.class, () -> SettingsLoader.load(badStarting, temp));
    }
}
