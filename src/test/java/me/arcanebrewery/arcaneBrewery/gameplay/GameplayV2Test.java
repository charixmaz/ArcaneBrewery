package me.arcanebrewery.arcaneBrewery.gameplay;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.application.aging.*;
import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

public class GameplayV2Test {
    public static ProcessProfile process(double temp,long seconds,String culture,String wood,long age,Map<String,Double> ingredients,Map<String,Double> additives) {
        return new ProcessProfile(ingredients,"distillation-v2",new FermentationProfile(culture,temp,seconds,100,0),
            new DistillationProfile(10,80,10,78,1),new AgingProfile(wood,age,0,18,100),additives);
    }
    public static ProcessProfile perfect() { return process(22,60,BrewingCulture.WILD.id(),"oak",120,Map.of("minecraft:wheat",1.0),Map.of()); }
    public static BatchConditions conditions(ProcessProfile p,double purity,double contamination) {
        var lots=new TreeMap<String,IngredientLot>();
        p.ingredientRatios().forEach((id,ratio)->lots.put(id,new IngredientLot(new IngredientProfile(id,50,70,25,20,70,0,60),purity)));
        return new BatchConditions(lots,contamination,100,new WoodCharacteristics(90,75,70));
    }
    @Test void scaledProportionsCollapseButIngredientAndDoseChangesDoNot() {
        var a=process(22,60,BrewingCulture.WILD.id(),"oak",120,Map.of("custom:herb",2.0,"minecraft:wheat",1.0),Map.of());
        var b=process(22,60,BrewingCulture.WILD.id(),"oak",120,Map.of("custom:herb",4.0,"minecraft:wheat",2.0),Map.of());
        assertEquals(FormulationIdentity.from(a),FormulationIdentity.from(b));
        var c=process(22,60,BrewingCulture.WILD.id(),"oak",120,Map.of("custom:herb",3.0,"minecraft:wheat",1.0),Map.of());
        assertNotEquals(FormulationIdentity.from(a),FormulationIdentity.from(c));
        assertNotEquals(FormulationIdentity.from(perfect()),FormulationIdentity.from(process(22,60,BrewingCulture.WILD.id(),"oak",120,Map.of("minecraft:wheat",1.0),Map.of("arcane:ember",1.0))));
    }
    @Test void setpointsTimesAndWoodsAreBatchDataButStylesAreDistinct() {
        var p=perfect();var altered=process(22.1,61,BrewingCulture.WILD.id(),"birch",200,p.ingredientRatios(),Map.of());
        assertEquals(FormulationIdentity.from(p),FormulationIdentity.from(altered));
        assertNotEquals(DrinkFingerprint.from(p),DrinkFingerprint.from(altered));
        var style=process(28,90,BrewingCulture.LUMINOUS.id(),"oak",120,p.ingredientRatios(),Map.of());
        assertNotEquals(FormulationIdentity.from(p),FormulationIdentity.from(style));
    }
    @Test void perfectMildPathIsExactlyOneHundredWithoutAQualityRoll() {
        var p=perfect();var result=new ReproducibleQuality().assess(p,conditions(p,100,0),120);
        assertEquals(100,result.score(),1e-10);
        assertTrue(result.factors().stream().allMatch(f->f.score()==100));
        assertEquals(0,result.contaminationPenalty());
    }
    @Test void factorLossesAreMeasuredAndActionable() {
        var p=process(30,120,BrewingCulture.WILD.id(),"oak",240,perfect().ingredientRatios(),Map.of());
        var q=new ReproducibleQuality().assess(p,conditions(p,80,20),120);
        assertTrue(q.score()<80);assertEquals(36,q.factors().get(1).score());
        assertEquals(0,q.contaminationPenalty());assertTrue(q.breakdown().stream().anyMatch(s->s.contains("culture ideal 22")));
    }
    @Test void fullKnowledgeAndReproductionAcceptV2WithoutChangingV1Verification() {
        var p=perfect();var player=UUID.randomUUID();var personal=PlayerDiscovery.personal(player,p,Instant.EPOCH);
        assertInstanceOf(RecipeKnowledge.Full.class,personal.knowledgeFor(player));
        assertTrue(new ReproductionVerifier().reproduces(personal,process(24,70,BrewingCulture.WILD.id(),"birch",180,p.ingredientRatios(),Map.of())));
        assertFalse(new ReproductionVerifier().reproduces(PlayerDiscovery.discover(player,p,Instant.EPOCH),process(24,70,BrewingCulture.WILD.id(),"birch",180,p.ingredientRatios(),Map.of())));
        var renamed=personal.rename(player,"First");assertThrows(IllegalStateException.class,()->renamed.rename(player,"Second"));
    }
    @Test void capturedControlsRoundTripAndOldStateStillDecodes() {
        var p=perfect();var draft=new DistilleryProcessDraft(p.ingredientRatios(),Map.of("minecraft:wheat",100.0),"distillation-v2",new DistillationControls(82,2,96,2,1));
        var state=new DistilleryBatchState(1,UUID.randomUUID(),new MachineLocation(UUID.randomUUID(),1,2,3),draft,0,12000,DistilleryBatchState.Status.PROCESSING);
        assertEquals(state,new DistilleryStateCodec().decode(new DistilleryStateCodec().encode(state)));
        var fermented=new FermentationBatchState(1,UUID.randomUUID(),draft,new FermentationSettings(BrewingCulture.QUIET.id(),16,120,100,0),1,FermentationBatchState.Status.FERMENTING);
        assertEquals(fermented,new FermentationStateCodec().decode(new FermentationStateCodec().encode(fermented)));
    }
    @Test void v2AgingReportsPerfectScoreAndVariationCannotDenyIt() {
        var p=perfect();var draft=new DistilleryProcessDraft(p.ingredientRatios(),Map.of("minecraft:wheat",100.0),"distillation-v2",DistillationControls.DEFAULT);
        var source=new FermentedProcessDraft(draft,p.fermentation());
        var aging=new AgingBatchState(1,UUID.randomUUID(),new AgingSettings("oak",0,18,100),0,120);
        var a=new AgingService().finish(source,aging,120000,conditions(p,100,0).ingredients(),conditions(p,100,0).wood(),0,AgingBalance.targets(),1);
        var b=new AgingService().finish(source,aging,120000,conditions(p,100,0).ingredients(),conditions(p,100,0).wood(),0,AgingBalance.targets(),2);
        assertEquals(100,a.simulation().quality().overallScore(),1e-9);assertEquals(100,b.simulation().quality().overallScore(),1e-9);
        assertEquals(FormulationIdentity.from(a.process()),FormulationIdentity.from(b.process()));assertNotNull(a.assessment());
    }
    @Test void invalidNamesAndNonFiniteControlsFail() {
        var owner=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->PlayerDiscovery.personal(owner,perfect(),Instant.EPOCH).rename(owner,"§aFake"));
        assertThrows(IllegalArgumentException.class,()->new FermentationSettings(BrewingCulture.WILD.id(),Double.NaN,60,100,0));
        assertThrows(IllegalArgumentException.class,()->new ReproducibleQuality().assess(perfect(),conditions(perfect(),100,0),0));
    }
    @Test void actualStarterIngredientAndQuietStyleCanBothReachPerfectWorkmanship() {
        var registry=me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients.registry();
        for(var culture:List.of(BrewingCulture.WILD,BrewingCulture.QUIET)) {
            var p=process(culture.temperature(),culture.seconds(),culture.id(),"oak",120,Map.of("minecraft:wheat",1.0),Map.of());
            var c=new BatchConditions(Map.of("minecraft:wheat",new IngredientLot(registry.require("minecraft:wheat").profile(),100)),0,100,new WoodCharacteristics(90,75,70));
            assertEquals(100,new ReproducibleQuality().assess(p,c,120).score(),1e-9);
        }
        var mild=process(16,120,BrewingCulture.QUIET.id(),"oak",120,perfect().ingredientRatios(),Map.of());
        var strength=new me.arcanebrewery.arcaneBrewery.application.consumption.DrinkStrengthCalculator();
        assertTrue(strength.calculate(mild)<strength.calculate(perfect()));
    }
    @Test void ambiguousAccessiblePrefixesNeverGuess() {
        var ids=List.of(new DrinkFingerprint("abcdef"+"0".repeat(58)),new DrinkFingerprint("abcdef"+"1".repeat(58)));
        assertThrows(IllegalArgumentException.class,()->me.arcanebrewery.arcaneBrewery.application.discovery.RecipeIdResolver.resolve(ids,"abcdef"));
        assertEquals(ids.getFirst(),me.arcanebrewery.arcaneBrewery.application.discovery.RecipeIdResolver.resolve(ids,"abcdef0"));
    }
    @Test void practicalMaturityWindowAllowsReproduciblePerfectionAndThenPenalizesOveraging() {
        for(long seconds:List.of(120L,121L,126L,135L)) {
            var p=process(22,60,BrewingCulture.WILD.id(),"oak",seconds,perfect().ingredientRatios(),Map.of());
            assertEquals(100,new ReproducibleQuality().assess(p,conditions(p,100,0),120).score(),1e-9);
            assertEquals(FormulationIdentity.from(perfect()),FormulationIdentity.from(p));
        }
        var over=process(22,60,BrewingCulture.WILD.id(),"oak",180,perfect().ingredientRatios(),Map.of());
        assertTrue(new ReproducibleQuality().assess(over,conditions(over,100,0),120).score()<100);
    }
    @Test void screenshotRegressionEarlyWithdrawalAndExtremeOveragingCannotRetainHighQuality() {
        for(long[] example:List.of(new long[]{120,186},new long[]{78953,120})) {
            var p=process(22,60,BrewingCulture.WILD.id(),"oak",example[0],perfect().ingredientRatios(),Map.of());
            assertEquals(0,new ReproducibleQuality().assess(p,conditions(p,100,0),example[1]).score());
        }
        var ready=process(22,60,BrewingCulture.WILD.id(),"oak",187,perfect().ingredientRatios(),Map.of());
        assertEquals(100,new ReproducibleQuality().assess(ready,conditions(ready,100,0),186).score(),1e-9);
        var poor=process(30,60,BrewingCulture.WILD.id(),"oak",187,ready.ingredientRatios(),Map.of());
        assertTrue(new ReproducibleQuality().assess(poor,conditions(poor,100,0),186).score()<100);
    }

}
