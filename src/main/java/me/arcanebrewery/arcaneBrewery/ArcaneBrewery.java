package me.arcanebrewery.arcaneBrewery;

import org.bukkit.plugin.java.JavaPlugin;

public final class ArcaneBrewery extends JavaPlugin {

    @Override
    public void onEnable() {
        MessageConfig.setup(this);

        getServer().getPluginManager().registerEvents(new AgricultureListener(this), this);
        getServer().getPluginManager().registerEvents(new CauldronWasherEngine(this), this);
        getServer().getPluginManager().registerEvents(new DistilleryMenuHandler(this), this);
        getServer().getPluginManager().registerEvents(new AgingCaskEngine(this), this);
        getServer().getPluginManager().registerEvents(new CaskHygieneEngine(this), this);
        getServer().getPluginManager().registerEvents(new MethanolProducerEngine(this), this);
        getServer().getPluginManager().registerEvents(new StructureBreakListener(), this);
        getServer().getPluginManager().registerEvents(new CaskInteractListener(this), this);

        getLogger().info("ArcaneBrewery enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("ArcaneBrewery systems offline.");
    }
}
