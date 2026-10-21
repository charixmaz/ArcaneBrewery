package me.arcanebrewery.arcaneBrewery.application.aging;

import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgingServiceTest {
    private final AgingService service = new AgingService();

    private FermentedProcessDraft source() {
        var distillery = new DistilleryProcessDraft(
                Map.of("minecraft:wheat", 1.0),
                Map.of("minecraft:wheat", 90.0),
                "distillation",
                new DistillationControls(78.0, 10, 80, 10, 1)
        );
        return new FermentedProcessDraft(distillery, FermentationSettings.DEFAULT.profile());
    }

    private Map<String, IngredientLot> lots() {
        return Map.of("minecraft:wheat", new IngredientLot(
                new IngredientProfile("minecraft:wheat", 40, 80, 10, 8, 25, 0, 5), 90));
    }

    @Test
    void sameInputsAndSeedProduceSameFingerprintAndQuality() {
        var state = new AgingBatchState(AgingBatchState.VERSION,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var a = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var b = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        assertEquals(a.simulation().fingerprint(), b.simulation().fingerprint());
        assertEquals(a.simulation().quality(), b.simulation().quality());
    }

    @Test
    void agingDurationChangesIdentity() {
        var state = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var early = service.finish(source(), state, 61_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var mature = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        assertNotEquals(early.simulation().fingerprint(), mature.simulation().fingerprint());
    }

    @Test
    void physicalWoodChangesIdentity() {
        var oak = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var spruce = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("spruce", 12, 18, 95), 1_000);
        var a = service.finish(source(), oak, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var b = service.finish(source(), spruce, 121_000, lots(), AgingBalance.wood("spruce"),
                0, AgingBalance.targets(), 42);
        assertNotEquals(a.simulation().fingerprint(), b.simulation().fingerprint());
    }

    @Test
    void contaminationReducesQualityWithoutChangingIdentity() {
        var state = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var clean = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var dirty = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                100, AgingBalance.targets(), 42);
        assertEquals(clean.simulation().fingerprint(), dirty.simulation().fingerprint());
        assertTrue(clean.simulation().quality().overallScore() > dirty.simulation().quality().overallScore());
    }

    @Test
    void variationSeedChangesQualityButNotIdentity() {
        var state = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var a = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 1);
        var b = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 2);
        assertEquals(a.simulation().fingerprint(), b.simulation().fingerprint());
        assertNotEquals(a.simulation().quality(), b.simulation().quality());
    }
}
