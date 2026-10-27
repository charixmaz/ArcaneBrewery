package me.arcanebrewery.arcaneBrewery.application.alchemy;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.util.*;
import static me.arcanebrewery.arcaneBrewery.application.alchemy.ProfileEffect.*;
/** No batch UUID/seed input. All benefits derive from actual formulation, workmanship and captured conditions. */
public final class EffectProfileCalculator {
    public EffectProfile calculate(ProcessProfile p,double quality,double contamination,double equipmentStability) {
        for(double v:new double[]{quality,contamination,equipmentStability}) if(!Double.isFinite(v)||v<0||v>100) throw new IllegalArgumentException("invalid recorded condition");
        ReagentRegistry.validateCombination(p.alchemicalAdditives());
        var capability=EffectCapabilities.STARTER_INFUSER;
        record Candidate(ProfileEffect effect,int priority,int seconds) { }
        var candidates=new EnumMap<ProfileEffect,Candidate>(ProfileEffect.class);
        double duration=1,power=1,stability=equipmentStability;boolean risky=false;
        for(var entry:p.alchemicalAdditives().entrySet()) {
            var d=ReagentRegistry.additive(entry.getKey()).orElseThrow(()->new IllegalArgumentException("Unknown typed reagent: "+entry.getKey()));
            double dose=entry.getValue();if(dose<=0||dose>2) throw new IllegalArgumentException("starter reagent dose outside 0..2");
            switch(d.role()) {
                case EFFECT_CATALYST -> {
                    risky|=d.risky();power+=Math.max(0,dose-1)*.25;
                    for(var effect:d.effects()) {
                        var next=new Candidate(effect,d.priority(),d.baseSeconds());var old=candidates.get(effect);
                        if(old==null || next.priority()>old.priority()) candidates.put(effect,next);
                    }
                }
                case DURATION_REAGENT -> {duration*=1+(d.durationMultiplier()-1)*dose;power*=Math.pow(d.powerMultiplier(),dose);stability+=d.stabilization()*dose;}
                case POTENCY_REAGENT -> {power*=1+(d.powerMultiplier()-1)*dose;duration*=Math.pow(d.durationMultiplier(),dose);stability+=d.stabilization()*dose;}
                case STABILIZER -> {stability+=d.stabilization()*dose;power*=Math.pow(d.powerMultiplier(),dose);}
                case FUTURE_PURIFICATION -> throw new IllegalArgumentException("Purification is not available in Phase 1");
            }
        }
        if(candidates.isEmpty() && p.alchemicalAdditives().isEmpty()) {
            var natural=p.ingredientRatios().entrySet().stream().filter(e->e.getValue()>=.5)
                .map(e->NaturalEffectRegistry.ingredient(e.getKey())).flatMap(Optional::stream)
                .max(Comparator.comparingInt(NaturalEffectRegistry.Definition::priority));
            if(natural.isPresent()){var d=natural.get();candidates.put(d.effect(),new Candidate(d.effect(),d.priority(),d.seconds()));risky=d.risky();}
        }
        stability=Math.max(0,Math.min(100,stability-contamination*.4));
        var safety=risky||stability<40?EffectProfile.Safety.DANGEROUS:stability<75||contamination>15?EffectProfile.Safety.CAUTION:EffectProfile.Safety.SAFE;
        var out=new ArrayList<EffectProfile.Entry>();
        if(quality>0) {
            var ranked=candidates.values().stream().sorted(Comparator.comparingInt(Candidate::priority).reversed().thenComparingInt(c->c.effect().ordinal())).limit(capability.maximumBenefits()).toList();
            double execution=.35+.65*quality/100; // workmanship already incorporates real temperature/time/formulation
            for(var c:ranked) {
                var cap=capability.cap(c.effect());
                int amp=Math.min(cap.amplifier(),p.alchemicalAdditives().isEmpty()?0:(quality>=90 && power>=1 || quality>=80 && power>=1.5)?1:0);
                int seconds=(int)Math.floor(c.seconds()*duration*execution*(power>1?1/Math.sqrt(power):1));
                if(amp>0) seconds=(int)Math.floor(seconds*.75);
                out.add(new EffectProfile.Entry(c.effect(),Math.max(1,Math.min(cap.seconds(),seconds)),Math.min(cap.amplifier(),amp),false));
            }
            if(out.stream().anyMatch(e->e.effect()==HASTE)) out.add(new EffectProfile.Entry(HUNGER,Math.min((int)Math.round(15*duration),capability.cap(HUNGER).seconds()),0,true));
            if(out.stream().anyMatch(e->e.effect()==STRENGTH)) out.add(new EffectProfile.Entry(SLOWNESS,Math.min(12,(int)Math.round(8*duration)),0,true));
            if(safety==EffectProfile.Safety.DANGEROUS && !candidates.containsKey(STRENGTH) && !candidates.containsKey(CURSE)) out.add(new EffectProfile.Entry(WEAKNESS,8,0,true));
        }
        return new EffectProfile(EffectProfile.VERSION,capability,out,safety,stability);
    }
}
