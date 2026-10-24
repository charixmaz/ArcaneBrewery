package me.arcanebrewery.arcaneBrewery;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MessageConfigMigrationTest {
    @Test void upgradesExactStockButPreservesCustomizedValuesAndUnrelatedKeys() {
        var config=new YamlConfiguration();
        config.set("prefix","§d§l§o[Arcane] §f§o");
        config.set("producer_success","✔ Successfully collected 1x bottle of pure Industrial Methanol Fluid.");
        config.set("distillery_wash_first","My server's special washing instructions.");
        config.set("server-note","keep me");
        MessageConfig.migrate(config);
        assertEquals("My server's special washing instructions.",config.getString("distillery_wash_first"));
        assertEquals("keep me",config.getString("server-note"));
        assertTrue(config.getString("producer_success").contains("Cleansing Solution"));
        assertFalse(config.getString("prefix").contains("§o"));
        assertEquals(2,config.getInt("messages-version"));
    }
    @Test void repeatedMigrationIsIdempotentAndNewCustomizationWins() {
        var config=new YamlConfiguration(); MessageConfig.migrate(config);
        String first=config.saveToString(); MessageConfig.migrate(config); assertEquals(first,config.saveToString());
        config.set("names.infuser","My Custom Machine"); MessageConfig.migrate(config);
        assertEquals("My Custom Machine",config.getString("names.infuser"));
    }
    @Test void newerMessageSchemasAreNeverDowngraded() {
        var config=new YamlConfiguration(); config.set("messages-version",99); config.set("prefix","custom");
        String original=config.saveToString(); assertThrows(IllegalStateException.class,() -> MessageConfig.migrate(config));
        assertEquals(original,config.saveToString());
    }
}
