package me.arcanebrewery.arcaneBrewery.application.consumption;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntoxicationModelTest {
    private final IntoxicationModel model = new IntoxicationModel();

    @Test
    void strongerDrinkCausesMoreIntoxication() {
        var base = IntoxicationState.SOBER;
        var weak = model.consume(base, new DrinkConsumptionProfile(20, 80, 0, 0), 1_000);
        var strong = model.consume(base, new DrinkConsumptionProfile(70, 80, 0, 0), 1_000);
        assertTrue(strong.state().intoxication() > weak.state().intoxication());
    }

    @Test
    void toleranceReducesEffectiveDose() {
        var lowTolerance = new IntoxicationState(0, 0, 0, 0, 1_000);
        var highTolerance = new IntoxicationState(0, 80, 0, 0, 1_000);
        var drink = new DrinkConsumptionProfile(50, 80, 0, 0);
        assertTrue(model.consume(lowTolerance, drink, 2_000).state().intoxication()
                > model.consume(highTolerance, drink, 2_000).state().intoxication());
    }

    @Test
    void contaminationRaisesPoisoningWithoutBeingIdentity() {
        var clean = model.consume(IntoxicationState.SOBER,
                new DrinkConsumptionProfile(40, 80, 0, 0), 1_000);
        var dirty = model.consume(IntoxicationState.SOBER,
                new DrinkConsumptionProfile(40, 80, 80, 0), 1_000);
        assertTrue(dirty.state().poisoning() > clean.state().poisoning());
    }

    @Test
    void stateRecoversWithWallClockTime() {
        var state = new IntoxicationState(80, 40, 50, 20, 1_000);
        var recovered = model.recover(state, 3_601_000);
        assertTrue(recovered.intoxication() < state.intoxication());
        assertTrue(recovered.poisoning() < state.poisoning());
        assertTrue(recovered.tolerance() < state.tolerance());
        assertTrue(recovered.dependence() < state.dependence());
    }

    @Test
    void repeatedHeavyDrinkingCanBuildDependence() {
        var state = IntoxicationState.SOBER;
        var drink = new DrinkConsumptionProfile(80, 80, 0, 0);
        for (int i = 0; i < 20; i++) {
            state = model.consume(state, drink, 1_000 + i * 1_000L).state();
        }
        assertTrue(state.dependence() > 0);
    }

    @Test
    void withdrawalRequiresDependenceAndLowIntoxication() {
        var state = new IntoxicationState(0, 30, 0, 50, 1_000);
        assertTrue(model.withdrawalRisk(state, 2_000));
        assertFalse(model.withdrawalRisk(new IntoxicationState(50, 30, 0, 50, 1_000), 2_000));
    }
}
