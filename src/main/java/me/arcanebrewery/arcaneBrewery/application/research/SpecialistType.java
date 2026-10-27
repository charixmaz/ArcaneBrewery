package me.arcanebrewery.arcaneBrewery.application.research;
import java.util.*;
/** Stable research IDs; costs are levels, construction is a separate physical-machine operation. */
public enum SpecialistType {
 REAGENT_PROCESSOR("Reagent Processor","STONECUTTER","COPPER_BLOCK",6,"IRON_INGOT",4,"COPPER_INGOT",8,List.of("arcanebrewery:duration_reagent","arcanebrewery:invisibility_catalyst")),
 DURATION_INFUSER("Duration Infuser","LOOM","REDSTONE_BLOCK",8,"GOLD_INGOT",4,"REDSTONE_BLOCK",2,List.of("arcanebrewery:duration_reagent")),
 ESSENCE_CONCENTRATOR("Essence Concentrator","SMITHING_TABLE","IRON_BLOCK",12,"DIAMOND",2,"IRON_BLOCK",1,List.of("arcanebrewery:potency_reagent","arcanebrewery:combat_catalyst")),
 PURIFICATION_CHAMBER("Purification Chamber","GRINDSTONE","AMETHYST_BLOCK",10,"AMETHYST_BLOCK",4,"GOLD_INGOT",2,List.of("arcanebrewery:clarity_reagent","minecraft:honeycomb"));
 public final String label,core,foundation,buildA,buildB;public final int levels,amountA,amountB;public final List<String> materials;
 SpecialistType(String label,String core,String foundation,int levels,String a,int na,String b,int nb,List<String> materials){this.label=label;this.core=core;this.foundation=foundation;this.levels=levels;buildA=a;amountA=na;buildB=b;amountB=nb;this.materials=List.copyOf(materials);}
 public List<String> missing(Set<String> earned,Set<String> known,boolean recipe,int levelsNow){
  if(earned.contains(name()))return List.of();var out=new ArrayList<String>();
  if(this!=REAGENT_PROCESSOR&&!earned.contains(REAGENT_PROCESSOR.name()))out.add("Research Reagent Processor first");
  for(String id:materials)if(!known.contains(id))out.add("Discover "+id);
  if(this!=REAGENT_PROCESSOR&&!recipe)out.add("Independently discover one formulation");
  if(levelsNow<levels)out.add("Need "+levels+" XP levels (have "+levelsNow+")");return List.copyOf(out);
 }
}
