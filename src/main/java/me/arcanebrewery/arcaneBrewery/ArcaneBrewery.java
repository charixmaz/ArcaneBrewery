package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import me.arcanebrewery.arcaneBrewery.application.storage.StorageProvider;
import me.arcanebrewery.arcaneBrewery.storage.sqlite.SqliteStorageProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArcaneBrewery extends JavaPlugin {
    private IngredientItemAdapter ingredientItems;
    private StorageProvider storage;
    private DiscoveryRuntime discoveries;
    private DiscoveryQueryRuntime discoveryQueries;
    private DiscoveryMutationRuntime discoveryMutations;
    private AlchemyMachineHandler alchemyMachines;

    public IngredientItemAdapter ingredientItems() {
        return java.util.Objects.requireNonNull(ingredientItems, "ingredient adapter not initialized");
    }

    @Override
    public void onEnable() {
        MessageConfig.setup(this);
        ingredientItems = new IngredientItemAdapter(BuiltInIngredients.registry(), new IngredientMetadataCodec(this));

        try {
            storage = new SqliteStorageProvider(getDataFolder().toPath().resolve("arcanebrewery.db"));
            java.util.concurrent.Executor async = command ->
                    getServer().getScheduler().runTaskAsynchronously(this, command);
            discoveries = new DiscoveryRuntime(
                    new DiscoveryRegistrationService(storage.discoveries()),
                    async
            );
            discoveryQueries = new DiscoveryQueryRuntime(storage.discoveries(), async);
            discoveryMutations = new DiscoveryMutationRuntime(storage.discoveries(), async);
            getLogger().info("ArcaneBrewery storage ready (schema V" + storage.schemaVersion() + ").");
        } catch (RuntimeException failure) {
            getLogger().severe("ArcaneBrewery storage initialization failed: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new AgricultureListener(ingredientItems), this);
        getServer().getPluginManager().registerEvents(new CauldronWasherEngine(ingredientItems), this);
        getServer().getPluginManager().registerEvents(new DistilleryMenuHandler(this, ingredientItems), this);
        getServer().getPluginManager().registerEvents(new BreweryListener(this), this);
        alchemyMachines = new AlchemyMachineHandler(this);
        getServer().getPluginManager().registerEvents(alchemyMachines, this);
        getServer().getScheduler().runTask(this, alchemyMachines::resumeLoadedMachines);
        getServer().getPluginManager().registerEvents(new AgingCaskEngine(this, ingredientItems, discoveries), this);
        var consumption = new ConsumptionListener(this);
        getServer().getPluginManager().registerEvents(consumption, this);
        getServer().getScheduler().runTaskTimer(this, new ConsumptionRecoveryTask(consumption), 200L, 200L);
        getServer().getPluginManager().registerEvents(new CaskHygieneEngine(this), this);
        getServer().getPluginManager().registerEvents(new MethanolProducerEngine(this), this);
        getServer().getPluginManager().registerEvents(new StructureBreakListener(), this);
        getServer().getPluginManager().registerEvents(new CaskInteractListener(this), this);

        var brew = java.util.Objects.requireNonNull(getCommand("brew"), "brew command missing from plugin.yml");
        var discoveryCommand = new DiscoveryCommand(this, discoveryQueries, discoveryMutations, consumption);
        brew.setExecutor(discoveryCommand);
        brew.setTabCompleter(discoveryCommand);

        getLogger().info("ArcaneBrewery enabled.");
    }

    @Override
    public void onDisable() {
        if (alchemyMachines != null) {
            alchemyMachines.shutdown();
            alchemyMachines = null;
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (RuntimeException failure) {
                getLogger().warning("ArcaneBrewery storage shutdown failed: " + failure.getMessage());
            } finally {
                storage = null;
            }
        }
        getLogger().info("ArcaneBrewery systems offline.");
    }
}
