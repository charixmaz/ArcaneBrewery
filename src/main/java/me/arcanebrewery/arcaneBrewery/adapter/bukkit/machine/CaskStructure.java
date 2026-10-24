package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.BreweryData;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.sign.Side;

public final class CaskStructure {
    private CaskStructure() { }
    public static boolean loaded(Block block) { return block.getWorld().isChunkLoaded(block.getX() >> 4,block.getZ() >> 4); }
    public static Block adjacentBarrel(Block sign) {
        for (BlockFace face : BlockFace.values()) {
            Block block = sign.getRelative(face);
            if (loaded(block) && block.getType() == Material.BARREL) return block;
        }
        return null;
    }
    public static boolean hasSign(Block core) {
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = core.getRelative(face);
            if (loaded(neighbor) && neighbor.getState() instanceof Sign sign
                    && sign.getSide(Side.FRONT).getLine(0).contains("[Brewery]")) return true;
        }
        return false;
    }
    /** Same 3x3x3 / >=12 matching-wood rule as existing worlds. */
    public static String wood(Block core) {
        String wood = null; int count = 0;
        for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
            Block block = core.getRelative(x,y,z);
            if (!loaded(block)) return "UNKNOWN";
            String name = block.getType().name();
            boolean component = name.endsWith("_STAIRS") || name.endsWith("_SLAB") || name.endsWith("_PLANKS") || name.contains("_LOG");
            if (wood == null && component) wood = BreweryData.getWoodFromBlock(block.getType());
            if (wood != null && component && name.contains(wood)) count++;
        }
        return wood != null && count >= 12 ? wood : "UNKNOWN";
    }
}
