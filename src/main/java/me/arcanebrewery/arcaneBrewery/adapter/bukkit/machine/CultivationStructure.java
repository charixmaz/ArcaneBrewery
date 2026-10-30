package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import java.util.Optional;

/** Horizontal Barrel, wooden fence directly behind its front face, at the same height. */
public final class CultivationStructure {
 private CultivationStructure() { }
 public static Optional<BlockFace> rearFace(Block core){
  if(core.getType()!=Material.BARREL||!(core.getBlockData() instanceof Directional data))return Optional.empty();
  return switch(data.getFacing()){
   case NORTH,SOUTH,EAST,WEST->Optional.of(data.getFacing().getOppositeFace());
   default->Optional.empty();
  };
 }
 public static boolean woodenFence(Material material){return material.name().endsWith("_FENCE")&&material!=Material.NETHER_BRICK_FENCE;}
 public static boolean valid(Block core){
  var rear=rearFace(core);if(rear.isEmpty())return false;var face=rear.get();
  int x=core.getX()+face.getModX(),z=core.getZ()+face.getModZ();
  if(!core.getWorld().isChunkLoaded(x>>4,z>>4))return false;
  return woodenFence(core.getRelative(face).getType());
 }
 public static boolean component(Block core,Block changed){
  if(core.getX()==changed.getX()&&core.getY()==changed.getY()&&core.getZ()==changed.getZ())return true;
  return rearFace(core).map(f->core.getX()+f.getModX()==changed.getX()&&core.getY()==changed.getY()&&core.getZ()+f.getModZ()==changed.getZ()).orElse(false);
 }
}
