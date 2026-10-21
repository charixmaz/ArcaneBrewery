package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public final class AlchemyStructureScanner {
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private AlchemyStructureScanner() {}

    public static boolean isValid(Block core) {
        if (core == null || core.getType() != Material.ENCHANTING_TABLE) return false;
        if (core.getRelative(BlockFace.DOWN).getType() != Material.CRYING_OBSIDIAN) return false;
        for (BlockFace face : HORIZONTAL) {
            if (core.getRelative(face).getType() == Material.AMETHYST_BLOCK) return true;
        }
        return false;
    }
}
