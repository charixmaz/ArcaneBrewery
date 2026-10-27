package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
public final class DisplayOnlyItems {
    private static final NamespacedKey KEY=new NamespacedKey("arcanebrewery","ui_display_only");
    private DisplayOnlyItems() { }
    public static void mark(ItemMeta meta){meta.getPersistentDataContainer().set(KEY,PersistentDataType.BYTE,(byte)1);}
    public static boolean test(ItemStack item){return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(KEY);}
}
