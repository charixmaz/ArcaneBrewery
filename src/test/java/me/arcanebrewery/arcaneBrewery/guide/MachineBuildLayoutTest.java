package me.arcanebrewery.arcaneBrewery.guide;
import me.arcanebrewery.arcaneBrewery.application.research.SpecialistType;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MachineBuildLayoutTest {
 @Test void everyVisibleMachineHasBuildIconsWithoutOverwritingNavigation(){
  var catalog=new GuideCatalog(BuiltInIngredients.registry(),new ProcessingTimes(10000,60,120),8);
  for(var machine:catalog.machines()){var parts=MachineBuildLayout.parts(machine.icon());assertFalse(parts.isEmpty());var slots=new HashSet<Integer>();
   for(var part:parts){assertTrue(slots.add(part.slot()));assertTrue(part.slot()>=0&&part.slot()<27);}}
 }
 @Test void specialistPicturesMatchActualCoreAndFoundation(){for(var type:SpecialistType.values()){
  var parts=MachineBuildLayout.parts(Material.valueOf(type.core));
  assertEquals(Material.valueOf(type.core),parts.stream().filter(p->p.slot()==13).findFirst().orElseThrow().icon());
  assertEquals(Material.valueOf(type.foundation),parts.stream().filter(p->p.slot()==22).findFirst().orElseThrow().icon());
 }}
 @Test void caskExplainsFlexibleWoodCountWithoutPromisingReadiness(){var text=MachineBuildLayout.parts(Material.OAK_STAIRS).toString();assertTrue(text.contains("12 matching"));assertTrue(text.contains("3 × 3 × 3"));assertTrue(text.contains("[Alchemy]"));assertTrue(text.contains("no readiness indicator"));}
 @Test void descriptionsUseWhiteBodyColoredLabelsAndSpacing(){var lore=BrewingGuide.styledLore(List.of("Controls: click Start","Hold a bottle","§cDangerous reaction"));assertTrue(lore.get(0).startsWith("§b"));assertEquals("",lore.get(1));assertTrue(lore.get(2).startsWith("§f"));assertTrue(lore.get(4).startsWith("§c"));}
}
