package me.arcanebrewery.arcaneBrewery.application.alchemy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlchemyBalanceProfileTest {
    @Test
    void defaultProfilePreservesV116Thresholds() {
        var balance = AlchemyBalanceProfile.DEFAULT;
        assertEquals(8_000, balance.processingDurationMillis());
        assertEquals(55.0, balance.unstableThreshold());
        assertEquals(75.0, balance.failedThreshold());
        assertEquals(90.0, balance.criticalThreshold());
        assertEquals(1.5f, balance.criticalExplosionPower());
    }

    @Test
    void thresholdsMustBeStrictlyIncreasing() {
        assertThrows(IllegalArgumentException.class, () -> new AlchemyBalanceProfile(
                8_000, 75, 55, 90, 18, 16, .45, .35, 20, 25, 1.5f));
        assertThrows(IllegalArgumentException.class, () -> new AlchemyBalanceProfile(
                8_000, 55, 55, 90, 18, 16, .45, .35, 20, 25, 1.5f));
    }

    @Test
    void customThresholdsActuallyChangeFailureBand() {
        var preparation = new AlchemyPreparation(Map.of("arcanebrewery:fire_catalyst", 1.0));
        var strict = new AlchemyBalanceProfile(
                8_000, 10, 20, 30, 18, 16, .45, .35, 20, 25, 1.5f);
        var relaxed = new AlchemyBalanceProfile(
                8_000, 60, 80, 95, 18, 16, .45, .35, 20, 25, 1.5f);

        assertEquals(AlchemyFailureOutcome.UNSTABLE_SUCCESS,
                new AlchemyFailureModel(strict).assess(preparation, AlchemyMachineCondition.PRISTINE).outcome());
        assertEquals(AlchemyFailureOutcome.STABLE_SUCCESS,
                new AlchemyFailureModel(relaxed).assess(preparation, AlchemyMachineCondition.PRISTINE).outcome());
    }

    @Test
    void maintenanceRespectsConfiguredAmountsAndBounds() {
        var balance = new AlchemyBalanceProfile(
                8_000, 55, 75, 90, 18, 16, .45, .35, 30, 40, 1.5f);
        var service = new AlchemyMaintenanceService(balance);
        var condition = new AlchemyMachineCondition(80, 25);

        assertEquals(new AlchemyMachineCondition(100, 25), service.repair(condition));
        assertEquals(new AlchemyMachineCondition(80, 0), service.clean(condition));
    }
}
