package me.arcanebrewery.arcaneBrewery.application.consumption;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerConditionPolicyTest {
    private final PlayerConditionPolicy fantasy=new PlayerConditionPolicy(false);
    @Test void repeatedExposureNeverBuildsLegacyProgression() {
        var state=RecoveryCondition.sober();
        for (int i=1;i<=20;i++) state=fantasy.consume(state,new DrinkConsumptionProfile(90,90,0,80),i*1000);
        assertEquals(0,state.state().tolerance()); assertEquals(0,state.state().dependence()); assertEquals(0,state.withdrawal());
        assertEquals("Magical Overload",fantasy.displayStatus(state)); assertTrue(state.hangover()>0);
    }
    @Test void legacyImportKeepsOnlyTemporaryExposureAndDoesNotMutateSource() {
        var old=new RecoveryCondition(new IntoxicationState(35,80,12,90,1000),80,95,1000);
        var imported=PlayerConditionPolicy.importLegacy(old);
        assertEquals(35,imported.state().intoxication()); assertEquals(12,imported.state().poisoning());
        assertEquals(0,imported.hangover()); assertEquals(0,imported.withdrawal());
        assertEquals("Arcane Saturation",fantasy.displayStatus(imported));
        assertEquals(90,old.state().dependence()); assertEquals(95,old.withdrawal());
    }
    @Test void offlineRecoveryClearsTemporaryConditionsAndDoesNotCreateWithdrawal() {
        var condition=new RecoveryCondition(new IntoxicationState(100,0,60,0,1000),60,0,1000);
        var recovered=fantasy.recover(condition,3_601_000);
        assertEquals("Clear",fantasy.displayStatus(recovered)); assertEquals(0,recovered.state().intoxication());
        assertEquals(0,recovered.state().poisoning()); assertEquals(0,recovered.hangover()); assertEquals(0,recovered.withdrawal());
    }
    @Test void historicalContaminationDoesNotCreateSymptoms() {
        var result=fantasy.consume(RecoveryCondition.sober(),new DrinkConsumptionProfile(10,30,100,5),1000);
        assertEquals(0,result.state().poisoning()); assertNotEquals("Contamination Hazard",fantasy.displayStatus(result));
    }
    @Test void legacyProgressionRequiresExplicitOptIn() {
        var legacy=new PlayerConditionPolicy(true);
        var state=legacy.consume(RecoveryCondition.sober(),new DrinkConsumptionProfile(100,90,0,0),1000);
        assertTrue(state.state().tolerance()>0); assertTrue(state.state().dependence()>0);
        var dependent=new RecoveryCondition(new IntoxicationState(0,80,0,100,1000),0,0,1000);
        assertTrue(legacy.recover(dependent,3_601_000).withdrawal()>0);
        assertEquals(0,fantasy.recover(dependent,3_601_000).withdrawal());
    }
    @Test void recoveryIsReproducibleAcrossRestartAndBackwardClockIsSafe() {
        var original=new RecoveryCondition(new IntoxicationState(75,0,15,0,1000),40,0,1000);
        assertEquals(fantasy.recover(original,61_000),new PlayerConditionPolicy(false).recover(original,61_000));
        assertEquals(75,fantasy.recover(original,500).state().intoxication());
        assertThrows(IllegalArgumentException.class,() -> fantasy.recover(original,-1));
    }
}
