package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact wood counting must be independent of traversal order. */
class CaskStructureRegressionTest {
    @Test void stoneComponentsDoNotCountAsCaskWood() {
        var f=new MachineFixture();var core=f.put(0,64,0,Material.BARREL);var p=positions();
        for(int i=0;i<12;i++){var q=p.get(i);f.put(q[0],q[1],q[2],Material.STONE_STAIRS);}
        assertEquals("UNKNOWN",CaskStructure.wood(core));
    }
    @Test void twoQualifyingWoodsAreRejectedAsAmbiguous() {
        var f=new MachineFixture();var core=f.put(0,64,0,Material.BARREL);var p=positions();
        for(int i=0;i<24;i++){var q=p.get(i);f.put(q[0],q[1],q[2],i<12?Material.OAK_PLANKS:Material.BIRCH_PLANKS);}
        assertEquals("UNKNOWN",CaskStructure.wood(core));
    }
    private List<int[]> positions() {
        var out=new ArrayList<int[]>();
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
            if(x!=0||y!=0||z!=0)out.add(new int[]{x,64+y,z});
        return out;
    }
    @Test void nearbyOtherWoodDoesNotHideTwelveMatchingComponents() {
        var f=new MachineFixture();var core=f.put(0,64,0,Material.BARREL);var p=positions();
        for(int i=1;i<=12;i++){var q=p.get(i);f.put(q[0],q[1],q[2],Material.OAK_PLANKS);}
        assertEquals("OAK",CaskStructure.wood(core));
        var q=p.getFirst();f.put(q[0],q[1],q[2],Material.BIRCH_PLANKS);
        assertEquals("OAK",CaskStructure.wood(core));
    }
    @Test void oakAndDarkOakCannotBeCombined() {
        var f=new MachineFixture();var core=f.put(0,64,0,Material.BARREL);var p=positions();
        for(int i=0;i<12;i++){var q=p.get(i);f.put(q[0],q[1],q[2],i<6?Material.OAK_PLANKS:Material.DARK_OAK_PLANKS);}
        assertEquals("UNKNOWN",CaskStructure.wood(core));
    }
}
