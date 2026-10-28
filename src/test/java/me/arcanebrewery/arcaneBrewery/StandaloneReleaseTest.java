package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyRuntimeSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class StandaloneReleaseTest {
    @Test void sellingAndPricingAreAbsentFromPlayerCommandsAndHelp() {
        assertFalse(DiscoveryCommand.PLAYER_COMMANDS.contains("sell"));
        assertFalse(DiscoveryCommand.PLAYER_COMMANDS.contains("value"));
        for(var topic:BrewHelp.TOPICS) {
            var help=BrewHelp.lines(topic).toString();
            assertFalse(help.contains("/brew sell")); assertFalse(help.contains("/brew value"));
        }
        assertTrue(Arrays.stream(ArcaneBrewery.class.getDeclaredMethods()).noneMatch(m->m.getName().equals("settleMarketSale")));
    }
    @Test void descriptorAndDefaultsHaveNoEconomyHookButRetainProtectedHistoryInspection() throws Exception {
        String descriptor;
        try(var input=getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(input);descriptor=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        var yaml=new YamlConfiguration();yaml.loadFromString(descriptor);
        assertFalse(yaml.contains("softdepend"));assertFalse(yaml.contains("permissions.arcanebrewery.market.sell"));
        assertEquals("arcanebrewery.admin.market",yaml.getString("commands.brewmarket.permission"));
        assertEquals("op",yaml.getString("permissions.arcanebrewery.admin.market.default"));
        try(var input=getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(input);var defaults=new YamlConfiguration();defaults.loadFromString(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            assertFalse(defaults.contains("market"));
        }
    }
    @Test void previouslyEnabledObsoleteMarketSettingsDoNotAffectBrewingConfiguration() throws Exception {
        var config=new YamlConfiguration();
        config.loadFromString("market:\n  player-sales-enabled: true\n  sell-multiplier: broken\n  maximum-price: invalid\n");
        assertEquals(ProcessingTimes.from(new YamlConfiguration()),ProcessingTimes.from(config));
        assertEquals(AlchemyRuntimeSettings.from(new YamlConfiguration()),AlchemyRuntimeSettings.from(config));
    }
}
