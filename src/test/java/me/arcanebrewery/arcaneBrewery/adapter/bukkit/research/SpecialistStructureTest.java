package me.arcanebrewery.arcaneBrewery.adapter.bukkit.research;
import me.arcanebrewery.arcaneBrewery.application.research.SpecialistType;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;
class SpecialistStructureTest {
 static class Cell {
  final Material material;Cell above,below;final Block block;
  Cell(Material material){this.material=material;block=(Block)Proxy.newProxyInstance(Block.class.getClassLoader(),new Class<?>[]{Block.class},(p,m,a)->switch(m.getName()){
   case "getType"->this.material;case "getRelative"->(int)a[1]>0?above.block:below.block;default->throw new AssertionError(m.getName());});}
 }
 @Test void processorRecognizesStonecutterOnCopperFromEitherBlock(){
  var core=new Cell(Material.STONECUTTER);var foundation=new Cell(Material.COPPER_BLOCK);core.below=foundation;foundation.above=core;
  assertEquals(SpecialistType.REAGENT_PROCESSOR,SpecialistMachines.structure(core.block));
  assertSame(core.block,SpecialistMachines.coreFor(core.block));assertSame(core.block,SpecialistMachines.coreFor(foundation.block));
 }
 @Test void oldProcessorAcceptsEveryOxidationStageAndNewFoundation(){
  for(var m:java.util.List.of(Material.SMOOTH_STONE,Material.COPPER_BLOCK,Material.EXPOSED_COPPER,Material.WEATHERED_COPPER,Material.OXIDIZED_COPPER,Material.WAXED_COPPER_BLOCK,Material.WAXED_EXPOSED_COPPER,Material.WAXED_WEATHERED_COPPER,Material.WAXED_OXIDIZED_COPPER)){
   var core=new Cell(Material.STONECUTTER);var below=new Cell(m);core.below=below;below.above=core;
   assertEquals(SpecialistType.REAGENT_PROCESSOR,SpecialistMachines.structure(core.block));assertSame(core.block,SpecialistMachines.coreFor(below.block));
  }
 }
 @Test void everySpecialistUsesItsDocumentedVerticalStructure(){for(var type:SpecialistType.values()){
  var core=new Cell(Material.valueOf(type.core));var foundation=new Cell(Material.valueOf(type.foundation));core.below=foundation;foundation.above=core;
  assertEquals(type,SpecialistMachines.structure(core.block));assertSame(core.block,SpecialistMachines.coreFor(foundation.block));core.below=new Cell(Material.STONE);assertNull(SpecialistMachines.structure(core.block));
 }}
}
