package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
/** Cosmetic only, captured once; never contributes to quality or effects. */
public final class TastingNotes {
 private static final NamespacedKey KEY=new NamespacedKey("arcanebrewery","tasting_note_v1");
 private static final List<String> NOTES=List.of("Floral with a crisp finish","Earthy with a mellow finish","Sweet with a bright finish","Smoky with a dry finish","Herbal with a soft finish","Fruity with a warming finish","Nutty with a lingering finish","Spiced with a clean finish");
 private TastingNotes() { }
 public static void capture(ItemMeta meta,long seed){if(!meta.getPersistentDataContainer().has(KEY))meta.getPersistentDataContainer().set(KEY,PersistentDataType.STRING,NOTES.get(new SplittableRandom(seed).nextInt(NOTES.size())));}
 public static Optional<String> read(ItemMeta meta){String s=meta.getPersistentDataContainer().get(KEY,PersistentDataType.STRING);return s!=null&&NOTES.contains(s)?Optional.of(s):Optional.empty();}
}
