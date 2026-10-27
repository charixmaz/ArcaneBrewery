package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import java.util.*;
import java.util.function.*;
/** Read-only planning, then machine persistence before player mutation. No controls, cursor or hotbar gestures. */
public final class ShiftTransfers {
 private ShiftTransfers() { }
 public record Plan(ItemStack[] destination,ItemStack remainder) { }
 public static Plan plan(ItemStack source,ItemStack[] destination,int[] slots,IntUnaryOperator maximum,BiPredicate<Integer,ItemStack> accepts){
  var next=Arrays.stream(destination).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
  if(source==null||source.getAmount()<=0||DisplayOnlyItems.test(source))return new Plan(next,source);
  var remaining=source.clone();
  for(int pass=0;pass<2;pass++)for(int slot:slots){
   var existing=next[slot];if((pass==0)!=(existing!=null))continue;
   if(!accepts.test(slot,remaining)||existing!=null&&!existing.isSimilar(remaining))continue;
   int max=Math.min(maximum.applyAsInt(slot),remaining.getMaxStackSize());int room=max-(existing==null?0:existing.getAmount());int amount=Math.min(room,remaining.getAmount());
   if(amount<=0)continue;var moved=remaining.clone();moved.setAmount(amount+(existing==null?0:existing.getAmount()));next[slot]=moved;
   remaining.setAmount(remaining.getAmount()-amount);if(remaining.getAmount()==0)return new Plan(next,null);
  }return new Plan(next,remaining);
 }
 public static void move(InventoryClickEvent event,ItemStack[] machine,int[] guiSlots,int[] editable,IntUnaryOperator caps,BiPredicate<Integer,ItemStack> accepts,Consumer<ItemStack[]> commit){
  event.setCancelled(true);if(!(event.getWhoClicked() instanceof Player player))return;
  if(event.getClickedInventory()==event.getView().getTopInventory()){
   int index=-1;for(int i=0;i<guiSlots.length;i++)if(guiSlots[i]==event.getSlot())index=i;
   if(index<0||Arrays.stream(editable).noneMatch(x->guiSlots[x]==event.getSlot()))return;
   var source=machine[index];var storage=player.getInventory().getStorageContents();int[] all=java.util.stream.IntStream.range(0,storage.length).toArray();
   var plan=plan(source,storage,all,i->64,(i,item)->true);if(source==null||plan.remainder()!=null&&plan.remainder().getAmount()==source.getAmount())return;
   var next=machine.clone();next[index]=plan.remainder();commit.accept(next);player.getInventory().setStorageContents(plan.destination());
  }else if(event.getClickedInventory()==player.getInventory()){
   var source=event.getCurrentItem();var plan=plan(source,machine,editable,caps,accepts);
   if(source==null||plan.remainder()!=null&&plan.remainder().getAmount()==source.getAmount())return;
   commit.accept(plan.destination());event.setCurrentItem(plan.remainder());
  }
 }
}
