package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class CaskInteractListener implements Listener {

    private final JavaPlugin plugin;

    public CaskInteractListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private Block findAdjacentBarrel(Block signBlock) {
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = signBlock.getRelative(face);
            if (neighbor.getType() == Material.BARREL) {
                return neighbor;
            }
        }
        return null;
    }

    @EventHandler(ignoreCancelled=true)
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign sign)) return;

        if (sign.getLine(0).contains("[Brewery]")) {
            Block barrel = findAdjacentBarrel(block);
            if (barrel != null && barrel.getState() instanceof TileState ts) {
                NamespacedKey contaminationKey = new NamespacedKey(plugin, "cask_contaminated");
                int status = ts.getPersistentDataContainer().getOrDefault(contaminationKey, PersistentDataType.INTEGER, 0);

                if (status == 1) {
                    // Still fully infected, no methanol used yet
                    event.getPlayer().sendMessage("§c[ArcaneBrewery] ☣ Core contaminated! Flush the internal valves with Cleansing Solution first.");
                } else if (status == 2) {
                    // Methanol was injected, rebooting system to 0 (clean)
                    ts.getPersistentDataContainer().set(contaminationKey, PersistentDataType.INTEGER, 0);
                    ts.update();

                    sign.setLine(1, "§8Core Pressurized");
                    sign.setLine(2, "§2✔ ONLINE");
                    sign.setLine(3, "");
                    sign.update();

                    event.getPlayer().sendMessage("§a[ArcaneBrewery] ✔ System rebooted successfully. Cask is ready for new batches.");
                }
            }
        }
    }
}
