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
        if (core == null || !loaded(core) || core.getType() != Material.ENCHANTING_TABLE) return false;
        Block support = core.getRelative(BlockFace.DOWN);
        if (!loaded(support) || support.getType() != Material.CRYING_OBSIDIAN) return false;
        for (BlockFace face : HORIZONTAL) {
            Block adjacent = core.getRelative(face);
            if (loaded(adjacent) && adjacent.getType() == Material.AMETHYST_BLOCK) return true;
        }
        return false;
    }
    private static boolean loaded(Block block) {
        return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4);
    }
}
