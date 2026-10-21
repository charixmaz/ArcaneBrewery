package me.arcanebrewery.arcaneBrewery.application.consumption;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryLifecycleTest {
    private final RecoveryLifecycle lifecycle = new RecoveryLifecycle();

    @Test
    void heavyDrinkCreatesHangoverLoad() {
        var condition = lifecycle.afterDrink(
                RecoveryCondition.sober(),
                new DrinkConsumptionProfile(80, 70, 20, 0),
                1_000
        );
        assertTrue(condition.hangover() > 0);
    }

    @Test
    void hangoverRecoversWithWallClockTime() {
        var condition = new RecoveryCondition(
                new IntoxicationState(0, 10, 0, 0, 1_000),
                80, 0, 1_000
        );
        var recovered = lifecycle.recover(condition, 61_000);
        assertTrue(recovered.hangover() < condition.hangover());
    }

    @Test
    void dependenceCanProduceWithdrawalWhenSober() {
        var condition = new RecoveryCondition(
                new IntoxicationState(0, 20, 0, 80, 1_000),
                0, 0, 1_000
        );
        var recovered = lifecycle.recover(condition, 601_000);
        assertTrue(recovered.withdrawal() > 0);
        assertTrue(lifecycle.status(recovered) == RecoveryLifecycle.Status.WITHDRAWAL
                || lifecycle.status(recovered) == RecoveryLifecycle.Status.SEVERE_WITHDRAWAL);
    }

    @Test
    void drinkingCanRelieveSomeWithdrawalWithoutErasingDependence() {
        var condition = new RecoveryCondition(
                new IntoxicationState(0, 20, 0, 70, 1_000),
                0, 70, 1_000
        );
        var after = lifecycle.afterDrink(condition,
                new DrinkConsumptionProfile(60, 80, 0, 0), 2_000);
        assertTrue(after.withdrawal() < 70);
        assertTrue(after.state().dependence() > 0);
    }
}
