package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProductionLayoutTest {
 @Test void visualControlsAreUniqueAndNeverOverlapSavedInputs(){for(var layout:ProductionLayout.values()){var slots=new HashSet<Integer>(layout.inputs());for(var e:layout.controls().entrySet()){assertTrue(e.getKey()>=0&&e.getKey()<9);assertTrue(e.getValue()>=9&&e.getValue()<ProductionLayout.SIZE);assertTrue(slots.add(e.getValue()));assertEquals(e.getKey().intValue(),layout.logical(e.getValue()));}}}
 @Test void savedInputsRetainTheirIndexes(){assertEquals(Set.of(0,1,2,4,5),ProductionLayout.EXTRACTOR.inputs());assertEquals(Set.of(2,4,6),ProductionLayout.INFUSER.inputs());assertEquals(Set.of(0,2,4,6),ProductionLayout.CASK.inputs());for(var layout:ProductionLayout.values())for(int i:layout.inputs())assertEquals(i,layout.logical(i));}
 @Test void oldControlLocationsCannotTriggerInvisibleActions(){for(var layout:ProductionLayout.values())for(int old:layout.controls().keySet())assertEquals(-1,layout.logical(old));}
 @Test void bordersAndOutsideClicksAreNotControls(){for(var layout:ProductionLayout.values())for(int slot:new int[]{-999,-1,27,35,54})assertEquals(-1,layout.logical(slot));}
 @Test void startButtonsResolveToExistingActions(){assertEquals(8,ProductionLayout.EXTRACTOR.logical(25));assertEquals(8,ProductionLayout.INFUSER.logical(25));assertEquals(8,ProductionLayout.CULTIVATION.logical(22));assertEquals(-1,ProductionLayout.CASK.logical(25));}
}
