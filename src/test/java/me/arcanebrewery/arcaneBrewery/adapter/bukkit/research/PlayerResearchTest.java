package me.arcanebrewery.arcaneBrewery.adapter.bukkit.research;
import me.arcanebrewery.arcaneBrewery.application.research.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlayerResearchTest {
 static class Account {
  final PersistentDataContainer data=TestItems.stack(Material.PAPER,1).getItemMeta().getPersistentDataContainer();int level=50,total=500,saves;float progress=.4f;boolean fail;String saved;int savedLevel;
  Player player(){return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getPersistentDataContainer"->data;case "getLevel"->level;case "getTotalExperience"->total;case "getExp"->progress;
   case "giveExpLevels"->{level+=(Integer)a[0];yield null;}case "setLevel"->{level=(Integer)a[0];yield null;}case "setTotalExperience"->{total=(Integer)a[0];yield null;}case "setExp"->{progress=(Float)a[0];yield null;}
   case "saveData"->{if(fail)throw new IllegalStateException("save rejected");saves++;saved=data.get(new NamespacedKey("arcanebrewery","research_v1"),PersistentDataType.STRING);savedLevel=level;yield null;}default->throw new AssertionError(m.getName());});}
 }
 @Test void actualBoundarySavesXpAndUnlockTogetherAndDuplicateClicksCannotCharge(){var a=new Account();var p=a.player();var type=SpecialistType.REAGENT_PROCESSOR;PlayerResearch.purchase(p,type);assertEquals(0,a.level);assertEquals(0,a.savedLevel);assertEquals(Set.of(type.name()),ResearchProgress.decode(a.saved).earned());assertThrows(IllegalStateException.class,()->PlayerResearch.purchase(a.player(),type));assertEquals(0,a.level);assertEquals(1,a.saves);assertTrue(PlayerResearch.read(a.player()).earned().contains(type.name()));}
 @Test void saveFailureRestoresXpProgressAndMetadata(){var a=new Account();a.fail=true;assertThrows(IllegalStateException.class,()->PlayerResearch.purchase(a.player(),SpecialistType.REAGENT_PROCESSOR));assertEquals(50,a.level);assertEquals(500,a.total);assertEquals(.4f,a.progress);assertTrue(PlayerResearch.read(a.player()).earned().isEmpty());assertEquals(0,a.saves);}
 @Test void insufficientXpDoesNotMutateXpOrSavePlayer(){var a=new Account();a.level=49;assertThrows(IllegalStateException.class,()->PlayerResearch.purchase(a.player(),SpecialistType.REAGENT_PROCESSOR));assertEquals(49,a.level);assertEquals(0,a.saves);assertTrue(PlayerResearch.read(a.player()).earned().isEmpty());}
}
