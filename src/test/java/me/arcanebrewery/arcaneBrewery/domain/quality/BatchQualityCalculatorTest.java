package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchQualityCalculatorTest {
    private final BatchQualityCalculator calculator = new BatchQualityCalculator();

    private BatchSimulationResult result(ProcessProfile p, BatchConditions c) {
        return calculator.calculate(p, TARGETS, c, BatchVariation.NONE);
    }

    @Test
    void cleanConditionsOutperformContamination() {
        var clean = result(process(), clean());
        var dirty = result(process(), conditions(90, 95, 95, WOOD));
        assertTrue(clean.quality().overallScore() > dirty.quality().overallScore());
        assertEquals(Contamination.State.CLEAN, clean.contamination().state());
        assertEquals(Contamination.State.SPOILED, dirty.contamination().state());
        assertEquals(clean.fingerprint(), dirty.fingerprint());
    }

    @Test
    void correctTemperatureAndDurationOutperformExtremeConditions() {
        var ideal = result(process(), clean());
        assertTrue(ideal.quality().overallScore() > result(
                withFermentation(fermentation(90, 3600, 100, 0)), clean()).quality().overallScore());
        assertTrue(ideal.quality().overallScore() > result(
                withFermentation(fermentation(22, 100, 100, 0)), clean()).quality().overallScore());
    }

    @Test
    void sanitationAndOxygenExposureDeriveContamination() {
        var sanitized = result(process(), clean());
        var dirty = result(withFermentation(fermentation(22, 3600, 0, 0)), clean());
        var exposed = result(withFermentation(fermentation(22, 3600, 100, 100)), clean());
        assertTrue(sanitized.contamination().burden() < dirty.contamination().burden());
        assertTrue(sanitized.contamination().burden() < exposed.contamination().burden());
    }

    @Test
    void badCutsIncreaseToxicityAndReduceQuality() {
        var ideal = result(process(), clean());
        var bad = result(withDistillation(distillation(0, 100, 0, 1)), clean());
        assertTrue(bad.contamination().toxicity() > ideal.contamination().toxicity());
        assertEquals(Contamination.State.TOXIC, bad.contamination().state());
        assertTrue(bad.quality().overallScore() < ideal.quality().overallScore());
    }

    @Test
    void passesTradePurityForFlavorAndSkippedDistillationHasNoCutPenalty() {
        var once = result(process(), clean());
        var twice = result(withDistillation(distillation(10, 80, 10, 2)), clean());
        assertTrue(twice.quality().purity() >= once.quality().purity());
        assertTrue(twice.quality().flavor() < once.quality().flavor());
        var skipped = result(withDistillation(distillation(0, 0, 0, 0)), clean());
        assertEquals(0, skipped.contamination().toxicity());
    }

    @Test
    void suitableAgingImprovesFlavorButOverAgingAndStorageCanReduceQuality() {
        var mature = result(process(), clean());
        var young = result(withAging(aging("oak", 0, 0, 15, 100)), clean());
        var over = result(withAging(aging("oak", 72000, 0, 15, 100)), clean());
        var storedPoorly = result(withAging(aging("oak", 7200, 90, 50, 0)), clean());
        assertTrue(mature.quality().flavor() > young.quality().flavor());
        assertTrue(mature.quality().overallScore() > over.quality().overallScore());
        assertTrue(mature.quality().overallScore() > storedPoorly.quality().overallScore());
        assertTrue(storedPoorly.contamination().burden() > mature.contamination().burden());
        assertTrue(mature.quality().flavor() > result(process(),
                conditions(0, 95, 95, new WoodCharacteristics(0, 0, 0))).quality().flavor());
    }

    @Test
    void ingredientPurityCompositionAndCatalystStabilityMatter() {
        var ideal = result(process(), clean());
        assertTrue(ideal.quality().purity() > result(process(), conditions(0, 20, 95, WOOD)).quality().purity());
        assertTrue(ideal.quality().magicalPotency() > result(process(), conditions(0, 95, 0, WOOD))
                .quality().magicalPotency());
        var lots = new java.util.HashMap<>(clean().ingredients());
        var bland = new me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile(
                "honey", 0, 0, 30, 0, 0, 70, 0);
        lots.put("honey", new IngredientLot(bland, 95));
        var changed = result(process(), new BatchConditions(lots, 0, 95, WOOD));
        assertTrue(changed.quality().flavor() < ideal.quality().flavor());
        assertTrue(changed.contamination().toxicity() > ideal.contamination().toxicity());
    }

    @Test
    void fixedSeedIsReproducibleAndDifferentSeedsPreserveIdentity() {
        var first = calculator.calculate(process(), TARGETS, clean(), BatchVariation.fromSeed(42));
        assertEquals(first, calculator.calculate(process(), TARGETS, clean(), BatchVariation.fromSeed(42)));
        var second = calculator.calculate(process(), TARGETS, clean(), BatchVariation.fromSeed(43));
        assertNotEquals(first.quality(), second.quality());
        assertEquals(first.fingerprint(), second.fingerprint());
        for (long seed = 0; seed < 100; seed++) {
            var variation = BatchVariation.fromSeed(seed);
            assertTrue(Math.abs(variation.quality()) <= 2);
            assertTrue(Math.abs(variation.potency()) <= 2);
            assertTrue(Math.abs(variation.flavor()) <= 2);
        }
    }

    @Test
    void contaminationStatesHaveExplicitThresholds() {
        assertEquals(Contamination.State.CLEAN, new Contamination(0, 0).state());
        assertEquals(Contamination.State.MINOR, new Contamination(5, 0).state());
        assertEquals(Contamination.State.SIGNIFICANT, new Contamination(30, 0).state());
        assertEquals(Contamination.State.DANGEROUS, new Contamination(60, 0).state());
        assertEquals(Contamination.State.SPOILED, new Contamination(80, 0).state());
        assertEquals(Contamination.State.TOXIC, new Contamination(0, 50).state());
    }

    @Test
    void variationCannotChangeContaminationOrToxicity() {
        var dirty = conditions(85, 60, 50, WOOD);
        var badCuts = withDistillation(distillation(0, 100, 0, 1));
        var baseline = result(badCuts, dirty);
        var varied = calculator.calculate(badCuts, TARGETS, dirty, BatchVariation.fromSeed(99));
        assertEquals(baseline.contamination(), varied.contamination());
        assertEquals(baseline.quality().contamination(), varied.quality().contamination());
        assertEquals(baseline.fingerprint(), varied.fingerprint());
    }

    @Test
    void recipesUnsuitableForAgingReceiveNoAgingBenefit() {
        var targets = new QualityTargets(22, 3600, 0, 15, 15);
        var young = calculator.calculate(withAging(aging("oak", 0, 0, 15, 100)),
                targets, clean(), BatchVariation.NONE);
        var aged = calculator.calculate(process(), targets, clean(), BatchVariation.NONE);
        assertTrue(young.quality().flavor() > aged.quality().flavor());
    }

    @Test
    void rejectsMissingIngredientLots() {
        assertThrows(IllegalArgumentException.class, () -> result(process(),
                new BatchConditions(java.util.Map.of(), 0, 100, WOOD)));
    }
}
