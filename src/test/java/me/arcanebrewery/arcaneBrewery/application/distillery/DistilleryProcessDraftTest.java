package me.arcanebrewery.arcaneBrewery.application.distillery;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DistilleryProcessDraftTest {
    @Test
    void mapOrderDoesNotChangeDraft() {
        var first = new DistilleryProcessDraft(
                new LinkedHashMap<>(Map.of("minecraft:wheat", 0.75, "minecraft:honeycomb", 0.25)),
                new LinkedHashMap<>(Map.of("minecraft:wheat", 80.0, "minecraft:honeycomb", 60.0)),
                " Distillation ",
                DistillationControls.DEFAULT
        );
        var secondRatios = new LinkedHashMap<String, Double>();
        secondRatios.put("minecraft:honeycomb", 0.25);
        secondRatios.put("minecraft:wheat", 0.75);
        var secondPurity = new LinkedHashMap<String, Double>();
        secondPurity.put("minecraft:honeycomb", 60.0);
        secondPurity.put("minecraft:wheat", 80.0);
        var second = new DistilleryProcessDraft(secondRatios, secondPurity, "distillation", DistillationControls.DEFAULT);

        assertEquals(first, second);
    }

    @Test
    void premiumGrainAndWheatRemainDifferentIdentityInputs() {
        var wheat = new DistilleryProcessDraft(Map.of("minecraft:wheat", 1.0), Map.of("minecraft:wheat", 100.0),
                "distillation", DistillationControls.DEFAULT);
        var premium = new DistilleryProcessDraft(Map.of("arcanebrewery:premium_grain", 1.0),
                Map.of("arcanebrewery:premium_grain", 100.0), "distillation", DistillationControls.DEFAULT);
        assertNotEquals(wheat.ingredientRatios(), premium.ingredientRatios());
    }

    @Test
    void controlChangesAreIdentityRelevantDraftChanges() {
        var base = new DistilleryProcessDraft(Map.of("minecraft:wheat", 1.0), Map.of("minecraft:wheat", 80.0),
                "distillation", DistillationControls.DEFAULT);
        var hotter = new DistilleryProcessDraft(Map.of("minecraft:wheat", 1.0), Map.of("minecraft:wheat", 80.0),
                "distillation", new DistillationControls(82, 10, 80, 10, 1));
        assertNotEquals(base, hotter);
    }

    @Test
    void rejectsMismatchedPurityKeys() {
        assertThrows(IllegalArgumentException.class, () -> new DistilleryProcessDraft(
                Map.of("minecraft:wheat", 1.0), Map.of("minecraft:apple", 80.0),
                "distillation", DistillationControls.DEFAULT));
    }
}
