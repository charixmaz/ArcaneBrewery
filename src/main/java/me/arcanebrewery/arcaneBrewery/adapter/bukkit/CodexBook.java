package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import me.arcanebrewery.arcaneBrewery.guide.BrewingGuide;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
public final class CodexBook implements Listener {
 private static final NamespacedKey KEY=new NamespacedKey("arcanebrewery","codex_book_v1");
 private final BrewingGuide guide;
 public CodexBook(BrewingGuide guide){this.guide=guide;}
 public static boolean isBook(ItemStack item){return item!=null&&item.getType()==Material.ENCHANTED_BOOK&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().getOrDefault(KEY,PersistentDataType.INTEGER,0)==1;}
 public static void give(Player player){for(var item:player.getInventory().getContents())if(isBook(item)){player.sendMessage("§eYour Alchemy Codex is already in your inventory. Hold it and right-click.");return;}
  var item=new ItemStack(Material.ENCHANTED_BOOK);var meta=item.getItemMeta();meta.setDisplayName("§b§lArcaneAlchemy Codex");meta.setLore(List.of("§fRight-click to open your personal Codex.","§7No fixed perfect recipes; experiment and record."));meta.getPersistentDataContainer().set(KEY,PersistentDataType.INTEGER,1);DisplayOnlyItems.mark(meta);PresentationStyle.apply(meta);item.setItemMeta(meta);
  if(!player.getInventory().addItem(item).isEmpty())player.sendMessage("§eMake room in your inventory, then use /al guide again.");
 }
 @EventHandler public void open(PlayerInteractEvent event){if(event.getHand()!=EquipmentSlot.HAND||!isBook(event.getItem())||(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK))return;if(event.getClickedBlock()!=null&&event.useItemInHand()==Event.Result.DENY)return;event.setCancelled(true);if(event.getPlayer().hasPermission("arcanebrewery.use"))guide.open(event.getPlayer());}
}
