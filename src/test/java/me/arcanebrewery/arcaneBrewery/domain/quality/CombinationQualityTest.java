package me.arcanebrewery.arcaneBrewery.domain.quality;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CombinationQualityTest {
 final CombinationQuality policy=new CombinationQuality();
 final Map<String,IngredientLot> lots=Map.of("grain",new IngredientLot(new IngredientProfile("grain",10,80,5,0,20,0,5),100),"fruit",new IngredientLot(new IngredientProfile("fruit",80,5,70,0,60,0,0),100));
 ProcessProfile process(double grain,double temp,long seconds,double extraction,long age){return new ProcessProfile(Map.of("grain",grain,"fruit",1-grain),"distillation-v2",new FermentationProfile(BrewingCulture.WILD.id(),temp,seconds,100,0),new DistillationProfile(10,80,10,extraction,1),new AgingProfile("oak",age,0,18,100),Map.of());}
 BatchConditions conditions(){return new BatchConditions(lots,0,100,new WoodCharacteristics(90,75,70));}
 @Test void ingredientRatiosChangeReachableTargets(){var a=policy.targets(process(.9,22,60,78,300),lots);var b=policy.targets(process(.1,22,60,78,300),lots);assertNotEquals(a,b);assertEquals(0,a.temperature()%2);assertEquals(0,a.cultivation()%30);assertEquals(0,a.extraction()%2);}
 @Test void repeatedInputsAndMapOrderReproduceExactly(){var p=process(.7,22,90,78,300);var reversed=new LinkedHashMap<String,IngredientLot>();reversed.put("fruit",lots.get("fruit"));reversed.put("grain",lots.get("grain"));assertEquals(policy.targets(p,lots),policy.targets(p,reversed));assertEquals(policy.assess(p,conditions(),200),policy.assess(p,conditions(),200));}
 @Test void suitableProcessCanReach100WithoutRandomRoll(){var seed=process(.7,22,90,78,300);var t=policy.targets(seed,lots);var upstream=process(.7,t.temperature(),t.cultivation(),t.extraction(),300);var finalTarget=policy.targets(upstream,lots);assertEquals(100,policy.assess(process(.7,t.temperature(),t.cultivation(),t.extraction(),finalTarget.maturation()),conditions(),finalTarget.maturation()).score(),1e-9);}
 @Test void upstreamMistakesChangeMaturationResponse(){assertNotEquals(policy.targets(process(.7,22,60,78,300),lots).maturation(),policy.targets(process(.7,36,300,90,300),lots).maturation());}
 @Test void unfinishedAndSeverelyOveragedAreNotExcellent(){assertEquals(0,policy.assess(process(.7,22,60,78,100),conditions(),200).score());assertTrue(policy.assess(process(.7,22,60,78,5000),conditions(),200).score()<1);}
 @Test void nearbySettingsAreForgivingAndOptimalBeatsPoor(){var t=policy.targets(process(.7,22,90,78,300),lots);double good=policy.assess(process(.7,t.temperature(),t.cultivation(),t.extraction(),300),conditions(),300).score();double close=policy.assess(process(.7,t.temperature()+2,t.cultivation()+30,t.extraction(),300),conditions(),300).score();double poor=policy.assess(process(.7,36,300,60,300),conditions(),300).score();assertTrue(good>close);assertTrue(close>poor);assertTrue(close>80);}
 @Test void incompleteSnapshotsFailClosed(){assertThrows(IllegalArgumentException.class,()->policy.targets(process(.7,22,60,78,300),Map.of()));assertThrows(IllegalArgumentException.class,()->policy.assess(process(.7,22,60,78,300),conditions(),0));}
}
