package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class StructureBreakListener implements Listener {

    @EventHandler
    public void onStructureBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        Material type = broken.getType();

        // Target standard structural building blocks
        if (type == Material.BARREL || type == Material.GRAY_CONCRETE || type.name().endsWith("_STAIRS")
                || type.name().endsWith("_SLAB") || type.name().endsWith("_PLANKS") || type.name().endsWith("_LOG")) {

            // Scan a 2-block radius to find any attached operational signs
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        Block check = broken.getRelative(x, y, z);
                        if (check.getState() instanceof Sign sign) {
                            String line0 = sign.getLine(0);

                            // If a recognized machine is nearby, kill it!
                            if (line0.contains("[Brewery]") || line0.contains("[Producer]") || line0.contains("[Diagnostics]")) {
                                sign.setLine(0, "§c§l[BROKEN]");
                                sign.setLine(1, "§7Structure");
                                sign.setLine(2, "§7Compromised");
                                sign.setLine(3, "");
                                sign.update();

                                event.getPlayer().sendMessage(MessageConfig.get("structure_broken"));
                            }
                        }
                    }
                }
            }
        }
    }
}