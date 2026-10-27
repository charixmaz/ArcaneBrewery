package me.arcanebrewery.arcaneBrewery.adapter.bukkit.research;
import me.arcanebrewery.arcaneBrewery.application.research.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
/** Server-thread transaction: XP and earned flags live in the SAME Minecraft player save. No cross-database XP charge. */
public final class PlayerResearch {
 private static final NamespacedKey KEY=new NamespacedKey("arcanebrewery","research_v1");
 private PlayerResearch() { }
 public static ResearchProgress read(Player player){if(player.getPersistentDataContainer().has(KEY)&&!player.getPersistentDataContainer().has(KEY,PersistentDataType.STRING))throw new IllegalArgumentException("Invalid research metadata type");return ResearchProgress.decode(player.getPersistentDataContainer().get(KEY,PersistentDataType.STRING));}
 public static void purchase(Player player,SpecialistType type,Set<String> known,boolean recipe){
  var pdc=player.getPersistentDataContainer();String before=pdc.get(KEY,PersistentDataType.STRING);int levels=player.getLevel(),total=player.getTotalExperience();float progress=player.getExp();
  var purchase=read(player).purchase(type,known,recipe,levels);
  try{player.giveExpLevels(-type.levels);pdc.set(KEY,PersistentDataType.STRING,purchase.progress().encode());player.saveData();}
  catch(RuntimeException failure){player.setLevel(levels);player.setExp(progress);player.setTotalExperience(total);if(before==null)pdc.remove(KEY);else pdc.set(KEY,PersistentDataType.STRING,before);throw failure;}
 }
}
