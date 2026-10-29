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
    @Test void explicitV2PreservesOldQualityAndV3DoesNotChangeIdentity() {
        var old=source();var d=old.distillery();
        var modern=new FermentedProcessDraft(new DistilleryProcessDraft(d.ingredientRatios(),d.ingredientPurity(),"distillation-v2",d.controls()),old.fermentation());
        var state=new AgingBatchState(AgingBatchState.VERSION,UUID.randomUUID(),new AgingSettings("oak",0,18,100),1000,200,true);
        var baseline=service.finish(modern,state,201000,lots(),AgingBalance.wood("oak"),0,AgingBalance.targets(),42,Map.of(),100);
        var v2=service.finish(modern,state,201000,lots(),AgingBalance.wood("oak"),0,AgingBalance.targets(),42,Map.of(),100,2);
        var v3=service.finish(modern,state,201000,lots(),AgingBalance.wood("oak"),0,AgingBalance.targets(),42,Map.of(),100,3);
        assertEquals(baseline,v2);assertEquals(v2.simulation().fingerprint(),v3.simulation().fingerprint());
        assertNotEquals(v2.assessment().score(),v3.assessment().score());
        assertEquals(v3,service.finish(modern,state,201000,lots(),AgingBalance.wood("oak"),0,AgingBalance.targets(),987654,Map.of(),100,3));
    }
    @Test void capturedMaturityControlsIdentityWithoutReinterpretingOldStages() {
        var old=new AgingBatchState(1,UUID.randomUUID(),new AgingSettings("oak",12,18,95),1000);
        var configured=new AgingBatchState(1,UUID.randomUUID(),new AgingSettings("oak",12,18,95),1000,240);
        var at121=service.finish(source(),old,122_000,lots(),AgingBalance.wood("oak"),0,AgingBalance.targets(),42);
        var at126=service.finish(source(),old,127_000,lots(),AgingBalance.wood("oak"),0,AgingBalance.targets(),42);
        assertEquals(at121.simulation().fingerprint(),at126.simulation().fingerprint());
        var longer=service.finish(source(),configured,242_000,lots(),AgingBalance.wood("oak"),0,AgingBalance.targets(),42);
        assertEquals(240,longer.process().aging().durationSeconds());
        assertEquals(120,at121.process().aging().durationSeconds());
        assertNotEquals(longer.simulation().fingerprint(),at121.simulation().fingerprint());
    }

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
    void matureAgingUsesThirtySecondIdentityStages() {
        var state = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var first = service.finish(source(), state, 122_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var sameStage = service.finish(source(), state, 129_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var laterStage = service.finish(source(), state, 151_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);

        assertEquals(first.simulation().fingerprint(), sameStage.simulation().fingerprint());
        assertNotEquals(first.simulation().fingerprint(), laterStage.simulation().fingerprint());
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
    void historicalContaminationNoLongerReducesActiveBatchQuality() {
        var state = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var clean = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var dirty = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                100, AgingBalance.targets(), 42);
        assertEquals(clean.simulation().fingerprint(), dirty.simulation().fingerprint());
        assertEquals(clean.simulation().quality(), dirty.simulation().quality());
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

    @Test
    void preparedAdditiveChangesFinalIdentity() {
        var state = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 12, 18, 95), 1_000);
        var plain = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42);
        var prepared = service.finish(source(), state, 121_000, lots(), AgingBalance.wood("oak"),
                0, AgingBalance.targets(), 42,
                Map.of("arcanebrewery:fire_catalyst", 1.0));
        assertNotEquals(plain.simulation().fingerprint(), prepared.simulation().fingerprint());
    }
}
