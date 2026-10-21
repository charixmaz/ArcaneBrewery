package me.arcanebrewery.arcaneBrewery.application.alchemy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlchemyFailureModelTest {
    private final AlchemyFailureModel model = new AlchemyFailureModel();

    @Test
    void pristineSingleCatalystIsStable() {
        var result = model.assess(
                new AlchemyPreparation(Map.of("arcanebrewery:fire_catalyst", 1.0)),
                AlchemyMachineCondition.PRISTINE);
        assertEquals(AlchemyFailureOutcome.STABLE_SUCCESS, result.outcome());
        assertTrue(result.producesPreparedBase());
    }

    @Test
    void sameInputsAndConditionAlwaysProduceSameOutcome() {
        var preparation = new AlchemyPreparation(Map.of(
                "arcanebrewery:fire_catalyst", 1.0,
                "arcanebrewery:vision_catalyst", 1.0));
        var condition = new AlchemyMachineCondition(55, 30);

        assertEquals(model.assess(preparation, condition), model.assess(preparation, condition));
    }

    @Test
    void damagedDirtyMachineRaisesInstability() {
        var preparation = new AlchemyPreparation(Map.of("arcanebrewery:fire_catalyst", 1.0));
        var clean = model.assess(preparation, AlchemyMachineCondition.PRISTINE);
        var worn = model.assess(preparation, new AlchemyMachineCondition(20, 80));
        assertTrue(worn.instability() > clean.instability());
    }

    @Test
    void sufficientlyBadConditionCausesCriticalFailure() {
        var preparation = new AlchemyPreparation(Map.of(
                "arcanebrewery:fire_catalyst", 2.0,
                "arcanebrewery:curse_catalyst", 2.0));
        var result = model.assess(preparation, new AlchemyMachineCondition(0, 100));
        assertEquals(AlchemyFailureOutcome.CRITICAL_FAILURE, result.outcome());
        assertFalse(result.producesPreparedBase());
        assertTrue(result.integrityDamage() > 0);
    }

    @Test
    void wearIsBoundedAndMonotonic() {
        var condition = new AlchemyMachineCondition(20, 90).wear(50, 50);
        assertEquals(0, condition.integrity());
        assertEquals(100, condition.contamination());
    }
}
