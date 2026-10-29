package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BottleCratesTest {
 @Test void fullInventoryRejectsWithoutChangingExistingItems(){var inventory=new ItemStack[36];for(int i=0;i<36;i++)inventory[i]=TestItems.stack(Material.STONE,64);assertThrows(IllegalArgumentException.class,()->BottleCrates.add(inventory,TestItems.water()));for(var item:inventory)assertEquals(64,item.getAmount());}
 @Test void bottleSlotsAreConservedAndPlanningDoesNotMutateInput(){var inventory=new ItemStack[36];for(int i=0;i<27;i++)inventory[i]=TestItems.stack(Material.STONE,64);var before=inventory.clone();for(int i=0;i<9;i++)inventory=BottleCrates.add(inventory,TestItems.water());assertNull(before[27]);assertNotNull(inventory[35]);var full=inventory;assertThrows(IllegalArgumentException.class,()->BottleCrates.add(full,TestItems.water()));}
 @Test void recoveredBarrelMergesIntoExistingStack(){var inventory=new ItemStack[36];for(int i=0;i<36;i++)inventory[i]=TestItems.stack(Material.STONE,64);inventory[0]=TestItems.stack(Material.BARREL,63);var next=BottleCrates.add(inventory,TestItems.stack(Material.BARREL,1));assertEquals(64,next[0].getAmount());assertEquals(63,inventory[0].getAmount());}
}
