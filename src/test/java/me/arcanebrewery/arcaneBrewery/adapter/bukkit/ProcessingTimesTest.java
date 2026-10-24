package me.arcanebrewery.arcaneBrewery.adapter.bukkit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyRuntimeSettings;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingTimesTest {
    @Test void defaultsMatchEstablishedRuntimeDurations() {
        var settings=ProcessingTimes.from(new YamlConfiguration());
        assertEquals(10_000,settings.extractionMillis()); assertEquals(60,settings.cultivationSeconds()); assertEquals(120,settings.maturationSeconds());
        assertEquals(8_000,AlchemyRuntimeSettings.from(new YamlConfiguration()).balance().processingDurationMillis());
    }
    @Test void rejectsNonFiniteNonPositiveOversizedAndFractionalIdentityDurations() {
        for (String key:new String[]{"processing.extraction-seconds","processing.cultivation-seconds","processing.maturation-seconds"}) {
            for (Object value:new Object[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0,604_801,"five"}) {
                var config=new YamlConfiguration(); config.set(key,value);
                assertThrows(IllegalArgumentException.class,() -> ProcessingTimes.from(config),key+": "+value);
            }
        }
        var fractional=new YamlConfiguration(); fractional.set("processing.maturation-seconds",1.5);
        assertThrows(IllegalArgumentException.class,() -> ProcessingTimes.from(fractional));
        var tiny=new YamlConfiguration(); tiny.set("processing.extraction-seconds",0.00001);
        assertThrows(IllegalArgumentException.class,() -> ProcessingTimes.from(tiny));
    }
    @Test void newSettingsDoNotMutateExistingValueObjects() {
        var config=new YamlConfiguration(); var before=ProcessingTimes.from(config);
        config.set("processing.extraction-seconds",25); config.set("processing.cultivation-seconds",90); config.set("processing.maturation-seconds",240);
        var after=ProcessingTimes.from(config);
        assertEquals(10_000,before.extractionMillis()); assertEquals(25_000,after.extractionMillis());
        assertEquals(120,before.maturationSeconds()); assertEquals(240,after.maturationSeconds());
    }
}
