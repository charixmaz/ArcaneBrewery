package me.arcanebrewery.arcaneBrewery.application.alchemy;
import java.util.*;
/** Computed once, stored, and executed without another recipe or randomness calculation. */
public record EffectProfile(int version,EffectCapabilities capability,List<Entry> effects,Safety safety,double stability) {
    public static final int VERSION=1;
    public enum Safety { SAFE, CAUTION, DANGEROUS }
    public record Entry(ProfileEffect effect,int seconds,int amplifier,boolean sideEffect) {
        public Entry {Objects.requireNonNull(effect);if(effect==ProfileEffect.NONE || seconds<1 || amplifier<0) throw new IllegalArgumentException("invalid effect entry");}
    }
    public EffectProfile {
        if(version!=VERSION || capability==null || safety==null || !Double.isFinite(stability)||stability<0||stability>100) throw new IllegalArgumentException("invalid effect profile");
        effects=List.copyOf(effects);var seen=EnumSet.noneOf(ProfileEffect.class);int benefits=0;
        if(effects.size()>4) throw new IllegalArgumentException("too many effects");
        for(var e:effects) {var cap=capability.cap(e.effect());if(e.sideEffect()!=java.util.Set.of(ProfileEffect.HUNGER,ProfileEffect.SLOWNESS,ProfileEffect.WEAKNESS).contains(e.effect()))throw new IllegalArgumentException("incorrect side effect role");if(!seen.add(e.effect())||e.seconds()>cap.seconds()||e.amplifier()>cap.amplifier()) throw new IllegalArgumentException("effect exceeds capability");if(!e.sideEffect()) benefits++;}
        if(benefits>capability.maximumBenefits()) throw new IllegalArgumentException("too many benefits");
        if(seen.contains(ProfileEffect.SPEED)&&seen.contains(ProfileEffect.SLOWNESS)
            || seen.contains(ProfileEffect.STRENGTH)&&seen.contains(ProfileEffect.WEAKNESS)
            || seen.contains(ProfileEffect.HASTE)&&seen.contains(ProfileEffect.CURSE)
            || seen.contains(ProfileEffect.LEVITATION)&&(seen.contains(ProfileEffect.SLOW_FALLING)||seen.contains(ProfileEffect.JUMP_BOOST))
            || seen.contains(ProfileEffect.WITHER)&&seen.contains(ProfileEffect.REGENERATION)) throw new IllegalArgumentException("self-cancelling profile");
    }
    public List<String> summary() {
        var out=new ArrayList<String>();
        for(var e:effects) out.add(e.effect().name().replace('_',' ')+" "+(e.amplifier()+1)+" — "+e.seconds()+"s"+(e.sideEffect()?" (side effect)":""));
        out.add("Reaction: "+safety+"; stability "+Math.round(stability)+"/100");return List.copyOf(out);
    }
}
