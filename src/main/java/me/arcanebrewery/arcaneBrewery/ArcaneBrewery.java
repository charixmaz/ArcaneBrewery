package me.arcanebrewery.arcaneBrewery;

import org.bukkit.plugin.java.JavaPlugin;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;

public final class ArcaneBrewery extends JavaPlugin {
    private IngredientItemAdapter ingredientItems;

    public IngredientItemAdapter ingredientItems() {
        return java.util.Objects.requireNonNull(ingredientItems, "ingredient adapter not initialized");
    }

    @Override
    public void onEnable() {
        MessageConfig.setup(this);
        ingredientItems = new IngredientItemAdapter(BuiltInIngredients.registry(), new IngredientMetadataCodec(this));

        getServer().getPluginManager().registerEvents(new AgricultureListener(ingredientItems), this);
        getServer().getPluginManager().registerEvents(new CauldronWasherEngine(ingredientItems), this);
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
