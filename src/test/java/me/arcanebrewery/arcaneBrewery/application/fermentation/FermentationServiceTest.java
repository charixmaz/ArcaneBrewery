package me.arcanebrewery.arcaneBrewery.application.fermentation;

import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FermentationServiceTest {
    @Test
    void startsFromCanonicalDistilleryDraft() {
        var draft = new DistilleryProcessDraft(
                Map.of("arcanebrewery:premium_grain", 1.0),
                Map.of("arcanebrewery:premium_grain", 100.0),
                "distillation",
                new DistillationControls(79, 8, 84, 8, 2)
        );
        var batchId = UUID.randomUUID();
        var state = new FermentationService().start(batchId, draft, FermentationSettings.DEFAULT, 1234);
        assertEquals(batchId, state.batchId());
        assertEquals(draft, state.distillery());
        assertEquals(FermentationBatchState.Status.FERMENTING, state.status());
    }

    @Test
    void fermentationSettingsRemainIdentityRelevantForFutureProcess() {
        var first = new FermentationSettings("arcanebrewery:wild_yeast", 22, 60, 85, 10);
        var second = new FermentationSettings("arcanebrewery:wild_yeast", 24, 60, 85, 10);
        assertNotEquals(first.profile(), second.profile());
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class,
                () -> new FermentationSettings("yeast", 22, 0, 85, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new FermentationSettings("yeast", 22, 60, 120, 10));
    }
}
