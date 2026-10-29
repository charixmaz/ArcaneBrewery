package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
/** Separate quality policy, independent of effect generation and recipe identity. */
public final class QualityPolicyMetadata {
 private static final NamespacedKey KEY=new NamespacedKey("arcanebrewery","batch_quality_policy");
 private QualityPolicyMetadata() { }
 public static int read(ItemMeta meta){var data=meta.getPersistentDataContainer();if(!data.has(KEY))return 2;Integer v=data.get(KEY,PersistentDataType.INTEGER);if(v==null||(v!=2&&v!=3))throw new IllegalArgumentException("Unsupported quality policy");return v;}
 public static void write(ItemMeta meta,int version){if(version!=2&&version!=3)throw new IllegalArgumentException("Unsupported quality policy");meta.getPersistentDataContainer().set(KEY,PersistentDataType.INTEGER,version);}
}
