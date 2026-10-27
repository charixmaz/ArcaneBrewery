package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.research.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SpecialistRecoveryTest {
 @Test void persistedConstructionConsumedInputsAndLockedOutputSurviveReopenWithoutRecharging(){var fixture=new MachineFixture();var core=fixture.put(0,64,0,Material.STONECUTTER);var items=new HashMap<String,ItemStack>();var storage=new MachineInventoryStorage("arcanebrewery","legacy",null,item->{var key=UUID.randomUUID().toString();items.put(key,item.clone());return key.getBytes(java.nio.charset.StandardCharsets.UTF_8);},raw->items.get(new String(raw,java.nio.charset.StandardCharsets.UTF_8)).clone());var state=new SpecialistState(SpecialistType.REAGENT_PROCESSOR,true,10000,0);var next=new ItemStack[9];next[2]=TestItems.stack(Material.PRISMARINE_SHARD,1);var raw=storage.save(core,null,next,state.encode());var loaded=storage.load(core);assertNull(loaded.items()[0]);assertEquals(state,SpecialistState.decode(loaded.process()));var released=SpecialistState.decode(loaded.process()).complete(20000);storage.save(core,loaded.raw(),loaded.items(),released.encode());assertEquals(0,SpecialistState.decode(storage.load(core).process()).readyAt());assertTrue(SpecialistState.decode(storage.load(core).process()).built());assertEquals(1,storage.load(core).items()[2].getAmount());assertThrows(IllegalStateException.class,()->storage.save(core,raw,new ItemStack[9],state.encode()));}
}
