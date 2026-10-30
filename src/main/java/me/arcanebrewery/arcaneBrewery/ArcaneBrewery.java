package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyRuntimeSettings;
import java.util.concurrent.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.MarketDiagnosticsCommand;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import me.arcanebrewery.arcaneBrewery.application.storage.StorageProvider;
import me.arcanebrewery.arcaneBrewery.storage.sqlite.SqliteStorageProvider;
import org.bukkit.plugin.java.JavaPlugin;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.BukkitMachineAccess;

public final class ArcaneBrewery extends JavaPlugin {
    private IngredientItemAdapter ingredientItems;
    private StorageProvider storage;
    private DiscoveryRuntime discoveries;
    private DiscoveryQueryRuntime discoveryQueries;
    private DiscoveryMutationRuntime discoveryMutations;
    private AlchemyMachineHandler alchemyMachines;
    private BukkitMachineAccess machineAccess;
    private DistilleryMenuHandler extractors;
    private AgingCaskEngine casks;
    private MethanolProducerEngine cleansers;
    private me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.SpecialistMachines specialists;
    private me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.MaterialDiscoveryListener materialDiscoveries;
    private ExecutorService materialWorker;
    private ExecutorService discoveryWorker;
    public void reconcileMaterialInputs(org.bukkit.entity.Player player,org.bukkit.inventory.ItemStack[] inputs) {
        if(materialDiscoveries!=null)materialDiscoveries.reconcileOwnedInputs(player,inputs);
    }
    public BukkitMachineAccess machineAccess() { return java.util.Objects.requireNonNull(machineAccess); }

    public IngredientItemAdapter ingredientItems() {
        return java.util.Objects.requireNonNull(ingredientItems, "ingredient adapter not initialized");
    }

    @Override
    public void onEnable() {
        if(getServer().getPluginManager().getPlugin("ArcaneBrewery")!=null){getLogger().severe("Remove the old ArcaneBrewery JAR before loading ArcaneAlchemy.");getServer().getPluginManager().disablePlugin(this);return;}
        try{LegacyDataUpgrade.prepare(getDataFolder().toPath());}catch(java.io.IOException failed){getLogger().severe("Legacy data upgrade blocked: "+failed.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        MessageConfig.setup(this);
        saveDefaultConfig();
        ingredientItems = new IngredientItemAdapter(BuiltInIngredients.registry(), new IngredientMetadataCodec(this));

        AlchemyRuntimeSettings alchemySettings;
        ProcessingTimes timing;
        try {
            alchemySettings = AlchemyRuntimeSettings.from(getConfig());
            timing = ProcessingTimes.from(getConfig());
            Object legacy=getConfig().get("consumption.legacy-alcohol-mechanics",false);
            if (!(legacy instanceof Boolean)) throw new IllegalArgumentException("consumption.legacy-alcohol-mechanics must be true or false");
        } catch (RuntimeException failure) {
            getLogger().severe("ArcaneAlchemy configuration is invalid: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        discoveryWorker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),task->{
            var thread=new Thread(task,"ArcaneAlchemy-discovery"); thread.setDaemon(true); return thread;
        });
        java.util.concurrent.Executor async = discoveryWorker;

        try {
            storage = new SqliteStorageProvider(getDataFolder().toPath().resolve("arcanebrewery.db"));
            discoveries = new DiscoveryRuntime(
                    new DiscoveryRegistrationService(storage.gameplayDiscoveries()),
                    async
            );
            discoveryQueries = new DiscoveryQueryRuntime(storage.gameplayDiscoveries(), async);
            discoveryMutations = new DiscoveryMutationRuntime(storage.gameplayDiscoveries(), async);
            var historicalJournal = storage.marketJournal();
            int prepared = historicalJournal.prepared(1000).size();
            int unresolvedEscrow = storage.marketEscrow().unresolved(1000).size();
            if (prepared > 0 || unresolvedEscrow > 0) {
                getLogger().warning("Historical market records retained for manual review (selling removed): " + prepared
                        + " prepared transaction(s), " + unresolvedEscrow
                        + " unresolved escrow record(s). No automatic payout/refund was attempted.");
            }
            getLogger().info("ArcaneAlchemy storage ready (schema V" + storage.schemaVersion() + ").");
        } catch (RuntimeException failure) {
            getLogger().severe("ArcaneAlchemy storage initialization failed: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        machineAccess=new BukkitMachineAccess(this);
        getServer().getPluginManager().registerEvents(machineAccess,this);
        getServer().getPluginManager().registerEvents(new AgricultureListener(ingredientItems), this);
        getServer().getPluginManager().registerEvents(new CauldronWasherEngine(ingredientItems), this);
        extractors=new DistilleryMenuHandler(this,ingredientItems,machineAccess,timing);
        getServer().getPluginManager().registerEvents(extractors, this);
        getServer().getPluginManager().registerEvents(new BreweryListener(this,machineAccess,timing), this);
        alchemyMachines = new AlchemyMachineHandler(this, alchemySettings,machineAccess);
        getServer().getPluginManager().registerEvents(alchemyMachines, this);
        getServer().getScheduler().runTask(this, alchemyMachines::resumeLoadedMachines);
        casks=new AgingCaskEngine(this,ingredientItems,discoveries,machineAccess,timing);
        getServer().getPluginManager().registerEvents(casks, this);
        var consumption = new ConsumptionListener(this);
        getServer().getPluginManager().registerEvents(consumption, this);
        getServer().getScheduler().runTaskTimer(this, new ConsumptionRecoveryTask(consumption), 200L, 200L);
        getServer().getPluginManager().registerEvents(new me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationRefreshListener(this,discoveryQueries),this);

        cleansers=new MethanolProducerEngine(this);
        getServer().getPluginManager().registerEvents(cleansers, this);
        // Structure validation and cask reboot now run through the shared access
        // boundary. Do not register the old broad sign-mutating/reboot listeners.

        materialWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),task->{var thread=new Thread(task,"ArcaneAlchemy-materials");thread.setDaemon(true);return thread;});
        materialDiscoveries=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.MaterialDiscoveryListener(this,
            new me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.MaterialCatalog(ingredientItems),
            new MaterialDiscoveryRuntime(storage.materials(),materialWorker));
        getServer().getPluginManager().registerEvents(materialDiscoveries,this);
        specialists=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.SpecialistMachines(this);
        getServer().getPluginManager().registerEvents(specialists,this);
        var brew = java.util.Objects.requireNonNull(getCommand("arcanealchemy"), "brew command missing from plugin.yml");
        var guide = new me.arcanebrewery.arcaneBrewery.guide.BrewingGuide(this,
                new me.arcanebrewery.arcaneBrewery.guide.GuideCatalog(ingredientItems.registry(), timing,
                        getConfig().getDouble("alchemy.processing-seconds", 8.0)), discoveryQueries,materialDiscoveries);
        getServer().getPluginManager().registerEvents(guide, this);
        getServer().getPluginManager().registerEvents(new me.arcanebrewery.arcaneBrewery.adapter.bukkit.CodexBook(guide),this);
        var discoveryCommand = new DiscoveryCommand(
                this, discoveryQueries, discoveryMutations, consumption, guide);
        brew.setExecutor(discoveryCommand);
        brew.setTabCompleter(discoveryCommand);

        var brewMarket = java.util.Objects.requireNonNull(
                getCommand("brewmarket"), "brewmarket command missing from plugin.yml");
        var diagnostics = new MarketDiagnosticsCommand(
                this, storage.marketJournal(), storage.marketEscrow(), async);
        brewMarket.setExecutor(diagnostics);
        brewMarket.setTabCompleter(diagnostics);

        getServer().getPluginManager().registerEvents(new me.arcanebrewery.arcaneBrewery.adapter.bukkit.BottleCrates(),this);
        getLogger().info("ArcaneAlchemy enabled.");
    }

    @Override
    public void onDisable() {
        if(specialists!=null){specialists.close();specialists=null;}
        if(materialDiscoveries!=null){materialDiscoveries.close();materialDiscoveries=null;}
        if(materialWorker!=null){materialWorker.shutdown();try{if(!materialWorker.awaitTermination(5,TimeUnit.SECONDS))materialWorker.shutdownNow();}catch(InterruptedException e){Thread.currentThread().interrupt();materialWorker.shutdownNow();}materialWorker=null;}
        if (extractors!=null) { extractors.close(); extractors=null; }
        if (casks!=null) { casks.close(); casks=null; }
        if (cleansers!=null) { cleansers.close(); cleansers=null; }
        if (alchemyMachines != null) {
            alchemyMachines.shutdown();
            alchemyMachines = null;
        }
        if (machineAccess!=null) { machineAccess.close(); machineAccess=null; }
        if(discoveryWorker!=null) {
            discoveryWorker.shutdown();
            try { if(!discoveryWorker.awaitTermination(5,TimeUnit.SECONDS)) discoveryWorker.shutdownNow(); }
            catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); discoveryWorker.shutdownNow(); }
            discoveryWorker=null;
        }
        if (storage != null) {
            try {
                storage.close();
            } catch (RuntimeException failure) {
                getLogger().warning("ArcaneAlchemy storage shutdown failed: " + failure.getMessage());
            } finally {
                storage = null;
            }
        }
        getLogger().info("ArcaneAlchemy systems offline.");
    }
}
