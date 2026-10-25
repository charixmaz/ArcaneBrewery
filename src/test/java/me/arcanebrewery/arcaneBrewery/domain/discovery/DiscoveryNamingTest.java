package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscoveryNamingTest {
    private final DescriptiveNameGenerator generator = new DescriptiveNameGenerator();

    private ProcessProfile custom(Map<String, Double> ingredients) {
        var p = process();
        return new ProcessProfile(ingredients, p.preparationMethod(), p.fermentation(),
                p.distillation(), p.aging(), p.alchemicalAdditives());
    }

    @Test
    void customNamespacedIngredientsProduceDescriptiveNames() {
        var p = custom(Map.of("myalchemy:moon_berry", 1.0));
        var name = generator.generate(p);
        assertTrue(name.value().contains("Myalchemy Moon Ber"));
        assertTrue(name.value().contains("Oak-aged"));
        assertTrue(name.value().contains("Arcane"));
        assertTrue(name.value().endsWith("Elixir"));
        assertEquals(name, generator.generate(p));
        assertEquals(p, PlayerDiscovery.discover(DiscoveryFixtures.OWNER, p, DiscoveryFixtures.DISCOVERED_AT).process());
    }

    @Test
    void vanillaNamespaceIsHiddenAndPresentationDoesNotAlterExistingDiscovery() {
        var p = custom(Map.of("minecraft:wheat", 1.0));
        assertEquals("Oak-aged Wheat Arcane Elixir", generator.generate(p).value());
        var original = PlayerDiscovery.discover(DiscoveryFixtures.OWNER, p, DiscoveryFixtures.DISCOVERED_AT)
                .rename(DiscoveryFixtures.OWNER, "My Original Recipe");
        var restored = PlayerDiscovery.restore(original.snapshot());
        assertEquals("My Original Recipe", restored.displayName().value());
        assertEquals(original.fingerprint(), restored.fingerprint());
    }

    @Test
    void tiesAndMapOrderAreDeterministic() {
        Map<String, Double> first = new LinkedHashMap<>();
        first.put("mod:zebra", 1.0); first.put("mod:apple", 1.0);
        Map<String, Double> second = new LinkedHashMap<>();
        second.put(" MOD:APPLE ", 10.0); second.put(" MOD:ZEBRA ", 10.0);
        assertEquals(generator.generate(custom(first)), generator.generate(custom(second)));
        assertTrue(generator.generate(custom(first)).value().contains("Mod Apple"));
    }

    @Test
    void undistilledUnagedRecipeWithoutAdditivesIsDescribedAsBrew() {
        var p = process();
        var brew = new ProcessProfile(p.ingredientRatios(), "steep", p.fermentation(),
                distillation(0, 0, 0, 0), aging("none", 0, 0, 15, 100), Map.of());
        assertEquals("Wheat Elixir", generator.generate(brew).value());
    }

    @Test
    void generatedNamesStayWithinBoundsEvenForLongUnicodeIdentifiers() {
        var p = process();
        var longProcess = new ProcessProfile(Map.of("custom:" + "莓".repeat(1000), 1.0), "蒸".repeat(1000),
                p.fermentation(), p.distillation(), aging("樽".repeat(1000), 1, 0, 15, 100),
                p.alchemicalAdditives());
        var name = generator.generate(longProcess);
        assertTrue(name.value().codePointCount(0, name.value().length()) <= DrinkName.MAX_LENGTH);
        assertFalse(name.value().isBlank());
    }

    @Test
    void blankLongControlAndInvisibleNamesAreRejected() {
        for (String invalid : new String[]{"", "  ", "\u00a0", "x".repeat(81), "A\nB", "A\tB",
                "A\u0000B", "A\u007fB", "A\u200bB", "A\u202eB", "A\u2028B", "\ud800"}) {
            assertThrows(IllegalArgumentException.class, () -> new DrinkName(invalid));
        }
        assertThrows(NullPointerException.class, () -> new DrinkName(null));
    }

    @Test
    void nameLengthCountsUnicodeCodePointsAndPreservesPresentationCase() {
        assertEquals("Lunar Reserve", new DrinkName("  Lunar\u00a0\u00a0Reserve  ").value());
        assertEquals("🍷".repeat(80), new DrinkName("🍷".repeat(80)).value());
        assertThrows(IllegalArgumentException.class, () -> new DrinkName("🍷".repeat(81)));
    }

    @Test
    void generatedNamesAreNotUniqueRecipeIdentifiers() {
        var first = process();
        var second = withFermentation(fermentation(23, 3600, 100, 0));
        assertEquals(generator.generate(first), generator.generate(second));
        assertNotEquals(me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint.from(first),
                me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint.from(second));
    }
}
