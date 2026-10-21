package me.arcanebrewery.arcaneBrewery.application.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BatchIngredientExtractorTest {
    private final BatchIngredientExtractor extractor = new BatchIngredientExtractor(BuiltInIngredients.registry());
    private IngredientInput ingredient(String id, long quantity, int purity) {
        return new IngredientInput.Ingredient(new IngredientInstance(id, purity, quantity,
                IngredientModifiers.NONE, Optional.empty()));
    }

    @Test
    void quantitiesCombineAndProduceExpectedRatios() {
        var result = extractor.extract(List.of(ingredient("minecraft:wheat", 2, 50),
                ingredient("minecraft:wheat", 1, 50), ingredient("minecraft:honeycomb", 1, 100)));
        assertEquals(3, result.ingredients().get("minecraft:wheat").quantity());
        assertEquals(1, result.ingredients().get("minecraft:honeycomb").quantity());
        assertEquals(Map.of("minecraft:wheat", 0.75, "minecraft:honeycomb", 0.25), result.ratios());
    }

    @Test
    void purityIsWeightedByQuantityAndTransferredExactlyToSimulationLots() {
        var result = extractor.extract(List.of(ingredient("minecraft:wheat", 3, 100),
                ingredient("minecraft:wheat", 1, 0)));
        assertEquals(75, result.ingredients().get("minecraft:wheat").purity());
        assertEquals(75, result.simulationLots().get("minecraft:wheat").purity());
        assertEquals(BuiltInIngredients.registry().require("minecraft:wheat").profile(),
                result.simulationLots().get("minecraft:wheat").composition());
    }

    @Test
    void regularAndPremiumGrainNeverCombineAsOneIdentity() {
        var result = extractor.extract(List.of(ingredient("minecraft:wheat", 3, 50),
                ingredient(BuiltInIngredients.PREMIUM_GRAIN, 1, 75)));
        assertEquals(2, result.ingredients().size());
        assertEquals(0.25, result.ratios().get(BuiltInIngredients.PREMIUM_GRAIN));
    }

    @Test
    void modifiersAndProvenanceArePreservedByWeightedCombination() {
        var source = new IngredientProvenance("arcanebrewery:harvest", Optional.of(new UUID(0, 1)));
        var a = new IngredientInput.Ingredient(new IngredientInstance("minecraft:wheat", 50, 3,
                new IngredientModifiers(4, 2), Optional.of(source)));
        var b = ingredient("minecraft:wheat", 1, 100);
        var result = extractor.extract(List.of(a, b)).ingredients().get("minecraft:wheat");
        assertEquals(62.5, result.purity());
        assertEquals(new IngredientModifiers(3, 1.5), result.modifiers());
        assertEquals(Map.of(Optional.of(source), 3L, Optional.empty(), 1L), result.provenance());
    }

    @Test
    void slotOrderingAndStackSplittingDoNotAffectExtractionOrRatios() {
        var a = ingredient("minecraft:wheat", 2, 50);
        var b = ingredient("minecraft:wheat", 1, 100);
        var honey = ingredient("minecraft:honeycomb", 1, 75);
        var first = extractor.extract(List.of(a, b, honey));
        assertEquals(first, extractor.extract(List.of(honey, b, a)));
        assertEquals(first, extractor.extract(List.of(ingredient("minecraft:wheat", 1, 50), b,
                ingredient("minecraft:wheat", 1, 50), honey)));
        assertEquals(first.ratios(), extractor.extract(List.of(honey, a, b)).ratios());
    }

    @Test
    void decimalWeightedSumsStayOrderIndependentForManyContributions() {
        var inputs = new ArrayList<IngredientInput>();
        for (int i = 1; i <= 200; i++) inputs.add(new IngredientInput.Ingredient(new IngredientInstance(
                "minecraft:wheat", i % 101, i, new IngredientModifiers(0.1, 0.3), Optional.empty())));
        var expected = extractor.extract(inputs);
        Collections.reverse(inputs);
        assertEquals(expected, extractor.extract(inputs));
    }

    @Test
    void containersAndBaseLiquidsAreCountedSeparatelyAndExcludedFromRatios() {
        var result = extractor.extract(List.of(ingredient("minecraft:wheat", 3, 50),
                new IngredientInput.Container("minecraft:glass_bottle", 3),
                new IngredientInput.BaseLiquid("minecraft:water", 2), new IngredientInput.Empty()));
        assertEquals(Map.of("minecraft:glass_bottle", 3L), result.containers());
        assertEquals(Map.of("minecraft:water", 2L), result.baseLiquids());
        assertEquals(Map.of("minecraft:wheat", 1.0), result.ratios());
    }

    @Test
    void unknownIngredientsAndIngredientFreeInputsAreRejectedExplicitly() {
        assertThrows(UnsupportedIngredientException.class, () -> extractor.extract(
                List.of(ingredient("unknown:grain", 1, 50))));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(List.of(new IngredientInput.Container("minecraft:bucket", 1))));
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(List.of()));
    }

    @Test
    void quantityOverflowIsRejectedInsteadOfLosingCounts() {
        assertThrows(ArithmeticException.class, () -> extractor.extract(List.of(
                ingredient("minecraft:wheat", Long.MAX_VALUE, 50), ingredient("minecraft:wheat", 1, 50))));
    }

    @Test
    void extractionMapsAndNestedProvenanceAreImmutable() {
        var result = extractor.extract(List.of(ingredient("minecraft:wheat", 1, 50)));
        assertThrows(UnsupportedOperationException.class, () -> result.ingredients().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.ratios().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.simulationLots().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.ingredients().get("minecraft:wheat").provenance().clear());
    }
}
