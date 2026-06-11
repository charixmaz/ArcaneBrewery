package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class ArcaneBrewery extends JavaPlugin {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new AgricultureListener(this), this);
        getServer().getPluginManager().registerEvents(new CauldronWasherEngine(this), this);
        getServer().getPluginManager().registerEvents(new DistilleryMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new AgingCaskEngine(this), this);
        getServer().getPluginManager().registerEvents(new CaskHygieneEngine(this), this);

        // BULLETPROOF PERSISTENCE LOOP: Sweeps loaded blocks once every second to tick external world signs!
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : Bukkit.getWorlds()) {
                    for (Chunk chunk : world.getLoadedChunks()) {
                        for (BlockState tile : chunk.getTileEntities()) {
                            if (tile.getType() == Material.BARREL) {
                                // Forces diagnostics out into the physical blocks in real-time
                                CaskHygieneEngine.updateExternalSigns(ArcaneBrewery.this, tile.getBlock());
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);

        getLogger().info("==========================================");
        getLogger().info(" ArcaneBrewery Matrix Complete!            ");
        getLogger().info(" Core Global Sign Loop Online (0 Problems) ");
        getLogger().info("==========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("ArcaneBrewery systems offline.");
    }
}