package me.arcanebrewery.arcaneBrewery.gameplay;

import me.arcanebrewery.arcaneBrewery.application.aging.*;
import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeMaturationTest {
    private FermentedProcessDraft source() {
        var p=GameplayV2Test.perfect();
        return new FermentedProcessDraft(new DistilleryProcessDraft(p.ingredientRatios(),Map.of("minecraft:wheat",100.0),"distillation-v2",DistillationControls.DEFAULT),p.fermentation());
    }
    @Test void targetsAreBoundedRepeatableAndIndependentOfBatchIdentity() {
        var source=source();long target=RecipeMaturation.target(source,Map.of());
        assertTrue(target>=120 && target<=300);
        for(int i=0;i<30;i++) assertEquals(target,RecipeMaturation.target(source(),Map.of()));
        var results=new HashSet<Long>();
        for(int i=1;i<=80;i++) {
            long t=RecipeMaturation.target(source,Map.of("arcane:catalyst",(double)i));
            assertTrue(t>=120 && t<=300);results.add(t);
        }
        assertTrue(results.size()>20);
    }
    @Test void capturedTargetAndPolicyRoundTripWhileOldBatchesKeepTheirTiming() {
        var metadata=new AgingBatchMetadata("arcanebrewery");var meta=TestItems.water().getItemMeta();
        long target=RecipeMaturation.target(source(),Map.of());
        var state=new AgingBatchState(1,UUID.randomUUID(),new AgingSettings("oak",0,18,100),1000,target,true);
        metadata.write(meta,state);assertEquals(state,metadata.read(meta).orElseThrow());
        var old=new AgingBatchState(1,UUID.randomUUID(),state.settings(),1000,120);
        metadata.write(meta,old);assertEquals(old,metadata.read(meta).orElseThrow());
        assertFalse(old.recipeTiming());
    }
    private AgingResult finish(long elapsed,boolean policy,Map<String,Double> additives,double stability) {
        var source=source();long target=RecipeMaturation.target(source,additives);
        var state=new AgingBatchState(1,UUID.randomUUID(),new AgingSettings("oak",0,18,100),0,target,policy);
        var c=GameplayV2Test.conditions(GameplayV2Test.perfect(),100,0);
        return new AgingService().finish(source,state,elapsed*1000,c.ingredients(),c.wood(),0,AgingBalance.targets(),23,additives,stability);
    }
    @Test void firstSixtySecondsAreZeroQualityAndPoisonousIncludingBoundary() {
        for(long elapsed:List.of(0L,1L,59L,60L)) {
            var result=finish(elapsed,true,Map.of(),100);
            assertEquals(0,result.simulation().quality().overallScore());
            assertEquals(100,result.simulation().quality().contamination());
            assertEquals(0,result.assessment().score());
        }
        assertFalse(RecipeMaturation.poisonous(61));
        assertEquals(0,finish(61,true,Map.of(),100).simulation().quality().overallScore());
        assertEquals(0,finish(60,false,Map.of(),100).simulation().quality().overallScore());
    }
    @Test void idealWindowIsRepeatableAndInfusedQualityCanReachOneHundred() {
        var additives=Map.of("arcanebrewery:fire_catalyst",1.0);
        long target=RecipeMaturation.target(source(),additives);
        for(long elapsed:List.of(target,target+7,target+15))
            assertEquals(100,finish(elapsed,true,additives,100).simulation().quality().overallScore(),1e-9);
        assertTrue(finish(target+30,true,additives,100).simulation().quality().overallScore()<100);
        assertTrue(finish(target,true,additives,70).simulation().quality().overallScore()<100);
    }
    @Test void stableCleanInfusionHasNoAutomaticPenaltyButDamageAndFailuresRemain() {
        var metadata=new AlchemyBatchConditionsMetadata("arcanebrewery");var meta=TestItems.water().getItemMeta();
        var model=new AlchemyMachineService();
        var catalysts=List.of(new AlchemyCatalyst("arcane:fire","arcane:fire",1,"Fire"));
        var success=model.assess(catalysts,AlchemyMachineCondition.PRISTINE);
        metadata.write(meta,AlchemyMachineCondition.PRISTINE,success);
        assertEquals(100,metadata.stability(meta));assertEquals(0,metadata.contamination(meta));
        var damaged=new AlchemyMachineCondition(30,50);
        metadata.write(meta,damaged,model.assess(catalysts,damaged));
        assertTrue(metadata.stability(meta)<100);assertTrue(metadata.contamination(meta)>0);
        metadata.write(meta,AlchemyMachineCondition.PRISTINE,success);
        assertTrue(metadata.stability(meta)<100);assertTrue(metadata.contamination(meta)>0);
    }
    @Test void unfinishedBottleRemainsConsumableButCannotMasqueradeAsFinished() {
        var metadata=new FinalDrinkMetadata("arcanebrewery");var meta=TestItems.water().getItemMeta();
        var result=finish(60,true,Map.of(),100);
        var alchemy=new AlchemyResult(AlchemyEffect.NONE,0,0,0,false);
        metadata.write(meta,result,alchemy,new me.arcanebrewery.arcaneBrewery.application.economy.DrinkValueCalculator().calculate(result.process(),result.simulation().quality(),alchemy));
        metadata.maturation(meta,false,true,130,60);
        assertTrue(metadata.readConsumption(meta).isPresent());
        assertEquals(0,metadata.readConsumption(meta).orElseThrow().quality());
        assertTrue(metadata.prematurePoison(meta));assertEquals(130,metadata.maturityTarget(meta).orElseThrow());
        assertEquals("UNFINISHED",meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey("arcanebrewery","drink_stage"),org.bukkit.persistence.PersistentDataType.STRING));
    }
}
