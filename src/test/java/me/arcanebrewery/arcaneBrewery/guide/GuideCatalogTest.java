package me.arcanebrewery.arcaneBrewery.guide;

import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import me.arcanebrewery.arcaneBrewery.application.alchemy.BuiltInAlchemyCatalysts;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.WashableIngredients;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GuideCatalogTest {
    private final GuideCatalog catalog = new GuideCatalog(BuiltInIngredients.registry(), new ProcessingTimes(12500, 90, 180), 9);
    @Test void everyRegisteredIngredientIsRepresentedWithoutPromisingUniversalWashing() {
        var entries = catalog.ingredients();
        assertEquals(BuiltInIngredients.registry().definitions().size(), entries.size());
        assertEquals(WashableIngredients.MATERIALS.size(), entries.stream()
                .filter(e -> e.lines().contains("Starter-ready: washable in a water cauldron.")).count());
        var glowstone = entries.stream().filter(e -> e.icon() == Material.GLOWSTONE_DUST).findFirst().orElseThrow();
        assertTrue(glowstone.lines().contains("Registered ingredient; no ordinary washing path yet."));
        assertTrue(glowstone.lines().contains("Also supported as an Arcane Infuser catalyst."));
    }
    @Test void catalystCatalogTracksTheActualSupportedRegistry() {
        var entries = catalog.catalysts();
        assertEquals(BuiltInAlchemyCatalysts.all().size(), entries.size());
        for (var external : BuiltInAlchemyCatalysts.all().keySet())
            assertTrue(entries.stream().anyMatch(e -> e.icon() == Material.matchMaterial(external)));
        assertTrue(entries.stream().filter(e -> e.icon() == Material.WITHER_SKELETON_SKULL)
                .findFirst().orElseThrow().lines().stream().anyMatch(l -> l.contains("dangerous")));
    }
    @Test void machineDurationsReflectServerSettingsAndLegacyProducerIsExplicit() {
        var text = catalog.machines().toString();
        assertTrue(text.contains("12.5 seconds"));
        assertTrue(text.contains("90 seconds"));
        assertTrue(text.contains("learned through experimentation"));
        assertTrue(text.contains("9.0 seconds"));
        assertTrue(text.contains("Reagent Processor"));assertTrue(text.contains("Purification Chamber"));assertFalse(text.contains("contaminated"));
    }
    @Test void walkthroughsDoNotPretendToBeSeededNamedRecipes() {
        var text = catalog.starters().toString();
        assertTrue(text.contains("Record your settings"));
        assertFalse(text.contains("100%"));
        assertFalse(text.contains("guaranteed Fire Resistance"));
    }
    @Test void legacyReferencesUseActualProfilesAndExplainTheirLimits() {
        var entries = catalog.legacyProfiles();
        assertEquals(me.arcanebrewery.arcaneBrewery.BreweryData.RECIPES.size(), entries.size());
        assertTrue(entries.stream().allMatch(e -> e.lines().contains("Compatibility reference for older bottles only.")));
        assertTrue(entries.stream().allMatch(e -> e.lines().contains("Legacy outputs do not register procedural discoveries.")));
    }
    @Test void wrappingPreservesLongFingerprintCharactersAndBoundsTooltipWidth() {
        String fingerprint = "abcdef0123456789".repeat(4);
        var wrapped = BrewingGuide.wrap(List.of(fingerprint));
        assertEquals(fingerprint, String.join("", wrapped));
        assertTrue(wrapped.stream().allMatch(l -> l.length() <= 48));
        assertTrue(BrewingGuide.wrap(List.of("A long readable sentence ".repeat(9))).stream().allMatch(l -> l.length() <= 48));
    }
}
