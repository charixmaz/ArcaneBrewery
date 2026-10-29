package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.EffectProfileMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import org.bukkit.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BottleColorsTest {
 @Test void ordinaryWaterRemainsUnchanged(){var meta=(PotionMeta)TestItems.water().getItemMeta();BottleColors.apply(meta);assertNull(meta.getColor());assertFalse(meta.hasCustomEffects());}
 private PotionMeta finished(){
  var meta=(PotionMeta)TestItems.water().getItemMeta();var p=meta.getPersistentDataContainer();
  p.set(new NamespacedKey("arcanebrewery","drink_format"),PersistentDataType.INTEGER,4);
  p.set(new NamespacedKey("arcanebrewery","drink_stage"),PersistentDataType.STRING,"FINISHED");
  p.set(new NamespacedKey("arcanebrewery","drink_fingerprint"),PersistentDataType.STRING,"test");
  for(var name:List.of("drink_strength","drink_quality","drink_contamination","drink_magical_potency"))p.set(new NamespacedKey("arcanebrewery",name),PersistentDataType.DOUBLE,20d);
  return meta;
 }
 @Test void storedBenefitSetsTintWithoutChangingProfileStatsOrNativeEffects(){
  var meta=finished();var profile=new EffectProfile(1,EffectCapabilities.STARTER_INFUSER,List.of(new EffectProfile.Entry(ProfileEffect.SPEED,30,0,false)),EffectProfile.Safety.SAFE,100);
  var codec=new EffectProfileMetadata("arcanebrewery");codec.write(meta,profile);var keys=meta.getPersistentDataContainer().getKeys();BottleColors.apply(meta);
  assertEquals(BottleColors.color(ProfileEffect.SPEED),meta.getColor());assertEquals(profile,codec.read(meta).orElseThrow());assertFalse(meta.hasCustomEffects());
  assertEquals(org.bukkit.potion.PotionType.WATER,meta.getBasePotionType());assertEquals(keys,meta.getPersistentDataContainer().getKeys());
  assertEquals(20d,meta.getPersistentDataContainer().get(new NamespacedKey("arcanebrewery","drink_quality"),PersistentDataType.DOUBLE));
 }
 @Test void legacyElixirWithoutEffectsGetsAmberAndRetainsMetadata(){var meta=finished();BottleColors.apply(meta);assertEquals(BottleColors.color(ProfileEffect.NONE),meta.getColor());assertFalse(meta.hasCustomEffects());}
 @Test void knownBaseStagesHaveDistinctTintsAndPlainBottleNamesCannotTriggerThem(){
  var meta=(PotionMeta)TestItems.water().getItemMeta();meta.setDisplayName("Speed elixir");BottleColors.apply(meta);assertNull(meta.getColor());
  meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","distillery_format"),PersistentDataType.INTEGER,1);BottleColors.apply(meta);var refined=meta.getColor();assertNotNull(refined);
  meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","fermentation_format"),PersistentDataType.INTEGER,1);BottleColors.apply(meta);assertNotEquals(refined,meta.getColor());
 }
}
