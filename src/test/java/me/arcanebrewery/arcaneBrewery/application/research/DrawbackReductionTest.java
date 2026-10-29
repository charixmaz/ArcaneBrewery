package me.arcanebrewery.arcaneBrewery.application.research;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DrawbackReductionTest {
 EffectProfile profile(){return new EffectProfile(1,EffectCapabilities.DURATION_INFUSER,List.of(new EffectProfile.Entry(ProfileEffect.HASTE,60,0,false),new EffectProfile.Entry(ProfileEffect.HUNGER,20,0,true)),EffectProfile.Safety.CAUTION,80);}
 @Test void consumesOnlyNeededQuantityAndPreservesBenefits(){var r=DrawbackReduction.apply(profile(),ProfileEffect.HUNGER,5,45,64,0);assertEquals(4,r.consumed());assertEquals(19,r.totalSeconds());assertEquals(1,r.profile().effects().get(1).seconds());assertEquals(profile().effects().get(0),r.profile().effects().get(0));}
 @Test void repeatedTreatmentsCannotResetLimits(){var a=DrawbackReduction.apply(profile(),ProfileEffect.HUNGER,1,25,64,20);assertEquals(25,a.totalSeconds());assertThrows(IllegalArgumentException.class,()->DrawbackReduction.apply(a.profile(),ProfileEffect.HUNGER,1,25,1,25));var b=DrawbackReduction.apply(profile(),ProfileEffect.HUNGER,5,45,64,44);assertEquals(45,b.totalSeconds());assertEquals(1,b.consumed());assertThrows(IllegalArgumentException.class,()->DrawbackReduction.apply(b.profile(),ProfileEffect.HUNGER,5,45,64,45));}
 @Test void usefulEffectsAndAbsentTargetsCannotBeReduced(){assertThrows(IllegalArgumentException.class,()->DrawbackReduction.apply(profile(),ProfileEffect.HASTE,1,25,1,0));assertThrows(IllegalArgumentException.class,()->DrawbackReduction.apply(profile(),ProfileEffect.WITHER,1,25,1,0));}
}
