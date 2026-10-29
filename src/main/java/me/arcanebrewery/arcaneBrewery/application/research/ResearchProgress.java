package me.arcanebrewery.arcaneBrewery.application.research;
import java.util.*;
public record ResearchProgress(Set<String> earned) {
 public ResearchProgress {earned=Set.copyOf(earned);for(String id:earned)SpecialistType.valueOf(id);}
 public record Purchase(ResearchProgress progress,int remainingLevels) { }
 public Purchase purchase(SpecialistType type,int levels){
  if(levels<0)throw new IllegalArgumentException("invalid levels");
  if(earned.contains(type.name()))throw new IllegalStateException("Already researched; no XP charged");
  var missing=type.missing(earned,levels);if(!missing.isEmpty())throw new IllegalStateException(String.join("; ",missing));
  var next=new HashSet<>(earned);next.add(type.name());return new Purchase(new ResearchProgress(next),levels-type.levels);
 }
 public String encode(){return "1:"+String.join(",",new TreeSet<>(earned));}
 public static ResearchProgress decode(String value){if(value==null)return new ResearchProgress(Set.of());if(!value.startsWith("1:"))throw new IllegalArgumentException("Unknown research format");return new ResearchProgress(value.length()==2?Set.of():Set.of(value.substring(2).split(",")));}
}
