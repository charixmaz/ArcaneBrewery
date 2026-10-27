package me.arcanebrewery.arcaneBrewery.application.research;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import java.util.*;
/** One operation of each kind per bottle. No seed, identity or historical quality mutations. */
public final class SpecialistProcess {
 private SpecialistProcess() { }
 public record Result(EffectProfile profile,int history) { }
 public static Result apply(SpecialistType type,EffectProfile input,int history,ProfileEffect selected){
  if(history<0||history>7)throw new IllegalArgumentException("Invalid processing history");
  int bit=switch(type){case DURATION_INFUSER->1;case ESSENCE_CONCENTRATOR->2;case PURIFICATION_CHAMBER->4;default->throw new IllegalArgumentException("Processor makes reagents");};
  if((history&bit)!=0)throw new IllegalStateException("This bottle has already received this specialist process");
  if(input.effects().isEmpty())throw new IllegalArgumentException("No stored effects to process");
  var capability=type==SpecialistType.DURATION_INFUSER?EffectCapabilities.DURATION_INFUSER:type==SpecialistType.ESSENCE_CONCENTRATOR?EffectCapabilities.ESSENCE_CONCENTRATOR:input.capability();
  var entries=new ArrayList<EffectProfile.Entry>();boolean removed=false,changed=false,raised=false;
  for(var e:input.effects()){
   if(type==SpecialistType.PURIFICATION_CHAMBER&&e.effect()==selected){if(!e.sideEffect()&&!Set.of(ProfileEffect.WITHER,ProfileEffect.CURSE,ProfileEffect.HALLUCINATION,ProfileEffect.LEVITATION).contains(selected))throw new IllegalArgumentException("Select an unwanted effect");removed=true;continue;}
   var cap=capability.cap(e.effect());int seconds=e.seconds(),amp=e.amplifier();
   if(type==SpecialistType.DURATION_INFUSER&&!e.sideEffect()) {amp=0;seconds=Math.min(cap.seconds(),Math.max(seconds,seconds*12));}
   if(type==SpecialistType.ESSENCE_CONCENTRATOR&&!e.sideEffect()){amp=Math.min(cap.amplifier(),amp+1);seconds=Math.max(1,(int)(seconds*.35));raised|=amp>e.amplifier();}
   seconds=Math.min(seconds,cap.seconds());amp=Math.min(amp,cap.amplifier());changed|=seconds!=e.seconds()||amp!=e.amplifier();entries.add(new EffectProfile.Entry(e.effect(),seconds,amp,e.sideEffect()));
  }
  if(type==SpecialistType.PURIFICATION_CHAMBER&&!removed)throw new IllegalArgumentException("Selected unwanted effect is absent");
  if(type==SpecialistType.ESSENCE_CONCENTRATOR&&!raised)throw new IllegalArgumentException("None of these effect families can gain a tier");
  if(type!=SpecialistType.PURIFICATION_CHAMBER&&!changed)throw new IllegalArgumentException("No useful change within this capability");
  return new Result(new EffectProfile(1,capability,entries,input.safety(),input.stability()),history|bit);
 }
}
