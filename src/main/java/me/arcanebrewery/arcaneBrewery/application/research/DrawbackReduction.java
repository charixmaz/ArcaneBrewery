package me.arcanebrewery.arcaneBrewery.application.research;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import java.util.*;
/** All reduction is charged and cumulative per bottle; ordinary benefits are immutable. */
public final class DrawbackReduction {
 private DrawbackReduction() { }
 public record Result(EffectProfile profile,int consumed,int totalSeconds) { }
 public static Result apply(EffectProfile input,ProfileEffect selected,int secondsPerItem,int materialCap,int quantity,int already){
  if(!Set.of(ProfileEffect.HUNGER,ProfileEffect.SLOWNESS,ProfileEffect.WEAKNESS,ProfileEffect.WITHER,ProfileEffect.CURSE,ProfileEffect.HALLUCINATION,ProfileEffect.LEVITATION).contains(selected))throw new IllegalArgumentException("Select an unwanted effect");
  if(quantity<1||quantity>64||already<0||already>45||!((secondsPerItem==1&&materialCap==25)||(secondsPerItem==3&&materialCap==35)||(secondsPerItem==5&&materialCap==45)))throw new IllegalArgumentException("Invalid reduction inputs");
  var target=input.effects().stream().filter(e->e.effect()==selected).findFirst().orElseThrow(()->new IllegalArgumentException("Selected effect is absent"));
  int reduction=Math.min(Math.min(quantity*secondsPerItem,materialCap-already),target.seconds()-1);
  if(reduction<=0)throw new IllegalArgumentException("No further reduction available");
  var entries=input.effects().stream().map(e->e.effect()==selected?new EffectProfile.Entry(e.effect(),e.seconds()-reduction,e.amplifier(),e.sideEffect()):e).toList();
  return new Result(new EffectProfile(input.version(),input.capability(),entries,input.safety(),input.stability()),(reduction+secondsPerItem-1)/secondsPerItem,already+reduction);
 }
}
