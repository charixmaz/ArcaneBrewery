package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.HashMap;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulationValidationTest {
    private ProcessProfile ratios(Map<String, Double> ratios) {
        var p = process();
        return new ProcessProfile(ratios, "mash", p.fermentation(), p.distillation(), p.aging(), Map.of());
    }

    @Test
    void rejectsInvalidIngredientAmountsAndAmbiguousIds() {
        assertThrows(IllegalArgumentException.class, () -> ratios(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ratios(Map.of("wheat", 0.0)));
        assertThrows(IllegalArgumentException.class, () -> ratios(Map.of("wheat", -1.0)));
        assertThrows(IllegalArgumentException.class, () -> ratios(Map.of("wheat", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> ratios(Map.of("wheat", Double.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class, () -> ratios(Map.of("wheat", 1.0, " WHEAT ", 1.0)));
        assertThrows(IllegalArgumentException.class, () -> ratios(Map.of(" ", 1.0)));
        var nullAmount = new HashMap<String, Double>();
        nullAmount.put("wheat", null);
        assertThrows(NullPointerException.class, () -> ratios(nullAmount));
    }

    @Test
    void rejectsNonFiniteDomainInputs() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> fermentation(invalid, 3600, 100, 0));
            assertThrows(IllegalArgumentException.class, () -> fermentation(22, 3600, invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> aging("oak", 7200, invalid, 15, 100));
            assertThrows(IllegalArgumentException.class, () -> aging("oak", 7200, 0, invalid, 100));
            assertThrows(IllegalArgumentException.class, () -> distillation(invalid, 80, 10, 1));
            assertThrows(IllegalArgumentException.class, () -> new BatchQuality(invalid, 50, 50, 50, 0, 50));
            assertThrows(IllegalArgumentException.class, () -> new IngredientProfile(
                    "custom:grain", invalid, 0, 0, 0, 0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new Contamination(invalid, 0));
            assertThrows(IllegalArgumentException.class, () -> new BatchVariation(invalid, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new WoodCharacteristics(invalid, 50, 50));
        }
    }

    @Test
    void rejectsInvalidDurationsPassesCutsAndTargets() {
        assertThrows(IllegalArgumentException.class, () -> fermentation(22, -1, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> aging("oak", -1, 0, 15, 100));
        assertThrows(IllegalArgumentException.class, () -> distillation(10, 80, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> distillation(-1, 80, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> distillation(10, 100, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new QualityTargets(22, 0, 7200, 15, 10));
        assertThrows(IllegalArgumentException.class, () -> new QualityTargets(22, 3600, -1, 15, 10));
        assertThrows(IllegalArgumentException.class, () -> new QualityTargets(22, 3600, 7200, 15, 0));
        assertThrows(IllegalArgumentException.class, () -> new BatchVariation(2.1, 0, 0));
    }

    @Test
    void finitePercentagesAreSafelyBounded() {
        assertEquals(100, fermentation(22, 3600, 200, -50).sanitation());
        assertEquals(0, fermentation(22, 3600, 200, -50).oxygenExposure());
        assertEquals(100, new Contamination(200, -1).burden());
        assertEquals(0, new Contamination(200, -1).toxicity());
        assertEquals(0, new IngredientLot(clean().ingredients().get("wheat").composition(), -1).purity());
        assertEquals(100, conditions(200, 95, 200, WOOD).catalystStability());
    }

    @Test
    void extremePassCountsCannotOverflowIntoFlavorBonuses() {
        var calculator = new BatchQualityCalculator();
        var ordinary = calculator.calculate(process(), TARGETS, clean(), BatchVariation.NONE);
        var excessive = calculator.calculate(withDistillation(distillation(10, 80, 10, 1_000_000_000)),
                TARGETS, clean(), BatchVariation.NONE);
        assertEquals(0, excessive.quality().flavor());
        assertTrue(ordinary.quality().flavor() > excessive.quality().flavor());
    }

    @Test
    void lotsAreImmutableAndMustMatchTheirComposition() {
        var lots = new HashMap<>(clean().ingredients());
        var conditions = new BatchConditions(lots, 0, 95, WOOD);
        lots.clear();
        assertEquals(2, conditions.ingredients().size());
        assertThrows(UnsupportedOperationException.class, () -> conditions.ingredients().clear());
        assertThrows(IllegalArgumentException.class, () -> new BatchConditions(
                Map.of("wrong", clean().ingredients().get("wheat")), 0, 95, WOOD));
    }
}
