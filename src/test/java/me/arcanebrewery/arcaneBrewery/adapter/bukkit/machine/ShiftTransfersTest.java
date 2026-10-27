package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ShiftTransfersTest {
 @Test void partialStacksFillBeforeEmptySlotsAndConserveEveryItem(){var input=TestItems.stack(Material.WHEAT,20);var slots=new ItemStack[]{TestItems.stack(Material.WHEAT,5),null};var p=ShiftTransfers.plan(input,slots,new int[]{0,1},i->8,(i,item)->true);assertEquals(8,p.destination()[0].getAmount());assertEquals(8,p.destination()[1].getAmount());assertEquals(9,p.remainder().getAmount());assertEquals(20,input.getAmount());assertEquals(5,slots[0].getAmount());}
 @Test void fullOrIncompatibleInventoryLeavesSourceUntouched(){var input=TestItems.stack(Material.WHEAT,7);var p=ShiftTransfers.plan(input,new ItemStack[]{TestItems.stack(Material.WHEAT,64),TestItems.stack(Material.APPLE,1)},new int[]{0,1},i->64,(i,item)->true);assertEquals(7,p.remainder().getAmount());assertEquals(64,p.destination()[0].getAmount());}
 @Test void onlyValidSlotsReceiveExactSingleDoses(){var input=TestItems.stack(Material.BLAZE_ROD,64);var p=ShiftTransfers.plan(input,new ItemStack[3],new int[]{0,1,2},i->1,(i,item)->i!=0);assertNull(p.destination()[0]);assertEquals(1,p.destination()[1].getAmount());assertEquals(1,p.destination()[2].getAmount());assertEquals(62,p.remainder().getAmount());}
 @Test void decorativeItemsNeverSupplyInputsAndPlanningCannotConsumeOnFailure(){var icon=TestItems.stack(Material.BLAZE_ROD,1);icon.edit(DisplayOnlyItems::mark);var p=ShiftTransfers.plan(icon,new ItemStack[1],new int[]{0},i->1,(i,item)->true);assertNull(p.destination()[0]);assertEquals(1,p.remainder().getAmount());var real=TestItems.stack(Material.WHEAT,4);assertThrows(IllegalStateException.class,()->ShiftTransfers.plan(real,new ItemStack[1],new int[]{0},i->8,(i,item)->{throw new IllegalStateException();}));assertEquals(4,real.getAmount());}
}
