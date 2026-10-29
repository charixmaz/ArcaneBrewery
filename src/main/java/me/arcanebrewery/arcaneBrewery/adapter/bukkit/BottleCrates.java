package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ShiftTransfers;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineInventoryCodec;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
/** Portable nine-bottle packs; a single player-inventory save commits the whole transfer. */
public final class BottleCrates implements Listener {
 private static final NamespacedKey KEY=new NamespacedKey("arcanebrewery","bottle_crate_v1"),ID=new NamespacedKey("arcanebrewery","bottle_crate_id");
 private static final MachineInventoryCodec CODEC=new MachineInventoryCodec();
 private static final FinalDrinkMetadata DRINKS=new FinalDrinkMetadata("arcanebrewery");
 private static boolean crate(ItemStack i){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(KEY);}
 private static boolean bottle(ItemStack i){return i!=null&&i.getType()==Material.POTION&&i.getAmount()==1&&!DisplayOnlyItems.test(i)&&DRINKS.readConsumption(i.getItemMeta()).isPresent();}
 private static ItemStack[] copy(ItemStack[] items){return Arrays.stream(items).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
 static ItemStack[] add(ItemStack[] inventory,ItemStack item){var p=ShiftTransfers.plan(item,inventory,java.util.stream.IntStream.range(0,inventory.length).toArray(),i->64,(i,v)->true);if(p.remainder()!=null)throw new IllegalArgumentException("Not enough inventory space; nothing changed");return p.destination();}
 public static void command(Player player,String[] args){
  try{
   if(args.length<2)throw new IllegalArgumentException("/brew crate pack <1–9> | unpack | inspect");
   var before=copy(player.getInventory().getStorageContents());var next=copy(before);String message;
   if(args[1].equalsIgnoreCase("pack")){
    int count=args.length>2?Integer.parseInt(args[2]):9;if(count<1||count>9)throw new IllegalArgumentException("Pack 1–9 bottles");
    int barrel=-1;for(int i=0;i<next.length;i++)if(next[i]!=null&&next[i].getType()==Material.BARREL&&!next[i].hasItemMeta()){barrel=i;break;}
    if(barrel<0)throw new IllegalArgumentException("One ordinary Barrel is required");
    var bottles=new ArrayList<ItemStack>();for(int i=0;i<next.length&&bottles.size()<count;i++)if(bottle(next[i])){bottles.add(next[i].clone());next[i]=null;}
    if(bottles.size()!=count)throw new IllegalArgumentException("Not enough finished bottles; nothing changed");
    if(next[barrel].getAmount()==1)next[barrel]=null;else next[barrel].setAmount(next[barrel].getAmount()-1);
    var payload=new ArrayList<byte[]>();for(int i=0;i<9;i++)payload.add(i<count?bottles.get(i).serializeAsBytes():new byte[0]);
    var packed=new ItemStack(Material.BARREL);var meta=packed.getItemMeta();meta.getPersistentDataContainer().set(KEY,PersistentDataType.BYTE_ARRAY,CODEC.encode(new MachineInventoryCodec.Snapshot("bottle-crate-v1",payload)));meta.getPersistentDataContainer().set(ID,PersistentDataType.STRING,UUID.randomUUID().toString());
    meta.setDisplayName("§6Bottle Crate · "+count+"/9");meta.setLore(List.of("§7/brew crate inspect · view contents","§7/brew crate unpack · recover bottles and Barrel","§7Portable container; cannot be placed."));PresentationStyle.apply(meta);packed.setItemMeta(meta);next=add(next,packed);message="§aPacked "+count+" bottles.";
   }else{
    int slot=player.getInventory().getHeldItemSlot();var held=next[slot];if(!crate(held)||held.getAmount()!=1)throw new IllegalArgumentException("Hold one Bottle Crate");
    var raw=held.getItemMeta().getPersistentDataContainer().get(KEY,PersistentDataType.BYTE_ARRAY);if(raw==null)throw new IllegalArgumentException("Invalid crate data; retained");
    var snapshot=CODEC.decode(raw);if(!snapshot.process().equals("bottle-crate-v1"))throw new IllegalArgumentException("Unsupported crate data; retained");
    var contents=new ArrayList<ItemStack>();for(var data:snapshot.slots())if(data.length>0){var item=ItemStack.deserializeBytes(data);if(!bottle(item))throw new IllegalArgumentException("Invalid crate contents; retained");contents.add(item);}
    if(contents.isEmpty())throw new IllegalArgumentException("Empty crate data; retained");
    if(args[1].equalsIgnoreCase("inspect")){player.sendMessage("§6Bottle Crate · "+contents.size()+"/9");for(var item:contents)player.sendMessage("§7• "+item.getItemMeta().getDisplayName()+"§r");return;}
    if(!args[1].equalsIgnoreCase("unpack"))throw new IllegalArgumentException("Use pack, unpack or inspect");
    next[slot]=null;for(var item:contents)next=add(next,item);next=add(next,new ItemStack(Material.BARREL));message="§aUnpacked bottles and recovered the Barrel.";
   }
   try{player.getInventory().setStorageContents(next);player.saveData();}catch(RuntimeException failure){player.getInventory().setStorageContents(before);throw failure;}
   player.sendMessage(message);
  }catch(RuntimeException failure){player.sendMessage("§e"+(failure.getMessage()==null?"Crate unavailable; inventory retained":failure.getMessage()));}
 }
 @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)public void place(BlockPlaceEvent e){if(crate(e.getItemInHand())){e.setCancelled(true);e.getPlayer().sendMessage("§eUse /brew crate unpack; crates cannot be placed.");}}
 @EventHandler(priority=EventPriority.LOWEST)public void interact(PlayerInteractEvent e){if(crate(e.getItem())){e.setCancelled(true);if(e.getHand()==EquipmentSlot.HAND)e.getPlayer().sendMessage("§e/brew crate inspect | unpack");}}
}
