package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.*;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QualityPolicyMetadataTest {
 @Test void absentMetadataRemainsV2AndV3SurvivesCopies(){var item=TestItems.water();assertEquals(2,QualityPolicyMetadata.read(item.getItemMeta()));var meta=item.getItemMeta();QualityPolicyMetadata.write(meta,3);item.setItemMeta(meta);assertEquals(3,QualityPolicyMetadata.read(item.clone().getItemMeta()));}
 @Test void malformedVersionFailsClosed(){var meta=TestItems.water().getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","batch_quality_policy"),PersistentDataType.STRING,"3");assertThrows(IllegalArgumentException.class,()->QualityPolicyMetadata.read(meta));assertThrows(IllegalArgumentException.class,()->QualityPolicyMetadata.write(meta,9));}
}
