package me.arcanebrewery.arcaneBrewery.adapter.bukkit.research;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import java.util.*;
public final class PreparedReagents {
 public enum Kind { DURATION, POTENCY, PURIFICATION }
 private static final NamespacedKey KEY=new NamespacedKey("arcanebrewery","prepared_reagent_v1");
 private PreparedReagents() { }
 public static Optional<Kind> read(ItemStack item){
  if(item==null||DisplayOnlyItems.test(item)||!item.hasItemMeta())return Optional.empty();
  var p=item.getItemMeta().getPersistentDataContainer();if(!p.has(KEY))return Optional.empty();
  if(item.getType()!=Material.PRISMARINE_SHARD)throw new IllegalArgumentException("Wrong prepared reagent carrier");
  if(!p.has(KEY,PersistentDataType.STRING))throw new IllegalArgumentException("Invalid reagent metadata type");
  return Optional.of(Kind.valueOf(p.get(KEY,PersistentDataType.STRING)));
 }
 public static ItemStack create(Kind kind){return create(kind,ItemStack::new);}
 public static ItemStack create(Kind kind,java.util.function.Function<Material,ItemStack> factory){var item=factory.apply(Material.PRISMARINE_SHARD);var meta=item.getItemMeta();meta.getPersistentDataContainer().set(KEY,PersistentDataType.STRING,kind.name());meta.setDisplayName("§dPrepared "+kind.name().toLowerCase(Locale.ROOT)+" reagent§r");meta.setLore(List.of("§7Processed reagent; one specialist operation.§r"));item.setItemMeta(meta);return item;}
}
