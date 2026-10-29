package me.arcanebrewery.arcaneBrewery.application.research;
import java.util.*;
/** Stable unlock IDs; construction belongs to the physical machine. */
public enum SpecialistType {
 REAGENT_PROCESSOR("Reagent Processor","STONECUTTER","COPPER_BLOCK",50,"IRON_INGOT",4,"COPPER_INGOT",8),
 DURATION_INFUSER("Duration Infuser","LOOM","REDSTONE_BLOCK",50,"GOLD_INGOT",4,"REDSTONE_BLOCK",2),
 ESSENCE_CONCENTRATOR("Essence Concentrator","SMITHING_TABLE","IRON_BLOCK",50,"DIAMOND",2,"IRON_BLOCK",1),
 PURIFICATION_CHAMBER("Purification Chamber","GRINDSTONE","AMETHYST_BLOCK",50,"AMETHYST_BLOCK",4,"GOLD_INGOT",2);
 public final String label,core,foundation,buildA,buildB;public final int levels,amountA,amountB;
 SpecialistType(String label,String core,String foundation,int levels,String a,int na,String b,int nb){this.label=label;this.core=core;this.foundation=foundation;this.levels=levels;buildA=a;amountA=na;buildB=b;amountB=nb;}
 public List<String> missing(Set<String> earned,int levelsNow){
  if(earned.contains(name()))return List.of();
  return levelsNow<levels?List.of("Need "+levels+" XP levels (have "+levelsNow+")"):List.of();
 }
}
