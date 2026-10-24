package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public class DistilleryStructureScanner {

    public static boolean isValidDistillery(Block block) {
        if (block == null) return false;
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4,block.getZ() >> 4)) return false;

        // If clicking brewing stand, check underneath
        if (block.getType() == Material.BREWING_STAND) {
            return block.getRelative(BlockFace.DOWN).getType() == Material.CRAFTING_TABLE;
        }
        // If clicking crafting table, check above
        if (block.getType() == Material.CRAFTING_TABLE) {
            return block.getRelative(BlockFace.UP).getType() == Material.BREWING_STAND;
        }

        return false;
    }

    public static Block getDistilleryStand(Block clicked) {
        if (clicked.getType() == Material.BREWING_STAND) return clicked;
        if (clicked.getType() == Material.CRAFTING_TABLE) return clicked.getRelative(BlockFace.UP);
        return null;
    }
}
