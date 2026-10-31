package me.arcanebrewery.arcaneBrewery.application.consumption;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlayerConditionPolicyTest {
    private final PlayerConditionPolicy policy=new PlayerConditionPolicy();
    @Test void repeatedUseCausesOverloadThenDepletionBeforeFullRecovery() {
        var condition=RecoveryCondition.clear();
        for(int i=1;i<=20;i++) condition=policy.consume(condition,new DrinkConsumptionProfile(90,90,0,80),i*1000);
        assertEquals("Mana Overload",policy.displayStatus(condition));
        assertEquals(100,condition.state().load()); assertTrue(condition.depletion()>25);
        var recovering=policy.recover(condition,condition.updatedAtEpochMillis()+8*60_000);
        assertEquals("Mana Depletion",policy.displayStatus(recovering));
        assertTrue(recovering.state().load()<70); assertTrue(recovering.depletion()>=25);
        var clear=policy.recover(recovering,recovering.updatedAtEpochMillis()+60*60_000);
        assertEquals("Clear",policy.displayStatus(clear)); assertEquals(0,clear.state().load()); assertEquals(0,clear.depletion());
    }
    @Test void offlineRecoveryAndRestartHaveIdenticalResults() {
        var original=new RecoveryCondition(new ManaState(75,15,1000),40,1000);
        assertEquals(policy.recover(original,61_000),new PlayerConditionPolicy().recover(original,61_000));
        assertEquals(75,policy.recover(original,500).state().load());
        assertThrows(IllegalArgumentException.class,()->policy.recover(original,-1));
    }
    @Test void retiredContaminationDoesNotGenerateNewSymptoms() {
        var result=policy.consume(RecoveryCondition.clear(),new DrinkConsumptionProfile(10,30,100,5),1000);
        assertEquals(0,result.state().hazard()); assertEquals("Clear",policy.displayStatus(result));
    }
    @Test void thresholdsHaveConsistentNames() {
        assertEquals("Mana Charged",policy.displayStatus(new RecoveryCondition(new ManaState(12,0,1000),0,1000)));
        assertEquals("Mana Depletion",policy.displayStatus(new RecoveryCondition(new ManaState(0,0,1000),25,1000)));
        assertEquals("Mana Overload",policy.displayStatus(new RecoveryCondition(new ManaState(70,0,1000),50,1000)));
    }
    @Test void nonFiniteAndOutOfRangeConditionsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new ManaState(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new ManaState(101,0,0));
        assertThrows(IllegalArgumentException.class,()->new RecoveryCondition(ManaState.CLEAR,-1,0));
    }
}
