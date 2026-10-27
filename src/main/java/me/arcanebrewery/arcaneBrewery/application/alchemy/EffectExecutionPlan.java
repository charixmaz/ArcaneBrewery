package me.arcanebrewery.arcaneBrewery.application.alchemy;
import java.util.*;
/** Immutable execution intent. Every native effect and teleport appears at most once. */
public final class EffectExecutionPlan {
    private EffectExecutionPlan() { }
    public record Action(String key,int seconds,int amplifier) { }
    public static List<Action> from(EffectProfile profile) {
        var actions=new LinkedHashMap<String,Action>();
        for(var e:profile.effects())switch(e.effect()) {
            case HALLUCINATION -> {add(actions,new Action("NAUSEA",e.seconds(),0));add(actions,new Action("DARKNESS",e.seconds(),0));}
            case CURSE -> {add(actions,new Action("WEAKNESS",Math.min(10,e.seconds()),0));add(actions,new Action("SLOWNESS",e.seconds(),0));}
            default -> add(actions,new Action(e.effect().name(),e.seconds(),e.amplifier()));
        }
        return List.copyOf(actions.values());
    }
    private static void add(Map<String,Action> actions,Action next) {
        actions.merge(next.key(),next,(a,b)->new Action(a.key(),Math.max(a.seconds(),b.seconds()),Math.max(a.amplifier(),b.amplifier())));
    }
}
