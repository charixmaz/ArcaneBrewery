package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyRuntimeSettings;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.MarketRuntimeSettings;
import me.arcanebrewery.arcaneBrewery.application.economy.MarketQuoteService;
import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.OptionalArcaneEconomy;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.CanonicalMarketBottleReader;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.BukkitMarketAssetPort;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.BukkitMarketPlayerService;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.MarketDiagnosticsCommand;
import java.util.UUID;
import java.util.concurrent.*;
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
    private MarketQuoteService marketQuotes;
    private OptionalArcaneEconomy economy;
    private MarketSettlementRuntime marketSettlements;
    private ExecutorService marketWorker;
    private RecoverableMarketTransactionJournal marketJournal;
    private MarketAssetPort marketAssets;

    /** Integration entry point only: no market commands or inventory adapter yet.
     * Journal must be durable/idempotent; assets are invoked on the server thread.
     * Never block the server thread waiting on the returned future. */
    public CompletableFuture<MarketSettlementResult> settleMarketSale(
            MarketTransactionJournal journal, MarketAssetPort assets, UUID player,
            MarketAsset asset, MarketQuote quote, UUID transactionId) {
        var runtime = marketSettlements;
        var currentEconomy = economy;
        if (runtime == null || currentEconomy == null)
            return CompletableFuture.failedFuture(new IllegalStateException("market runtime unavailable"));
        return runtime.settleSale(journal, assets, currentEconomy, player, asset, quote, transactionId);
    }

    /** Built-in durable sale path. No player-facing command is enabled yet. */
    public CompletableFuture<MarketSettlementResult> settleMarketSale(
            UUID player, MarketAsset asset, MarketQuote quote, UUID transactionId) {
        var runtime = marketSettlements;
        var currentEconomy = economy;
        var journal = marketJournal;
        var assets = marketAssets;
        if (runtime == null || currentEconomy == null || journal == null || assets == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("market runtime unavailable"));
        }
        return runtime.settleSale(journal, assets, currentEconomy, player, asset, quote, transactionId);
    }

    public IngredientItemAdapter ingredientItems() {
        return java.util.Objects.requireNonNull(ingredientItems, "ingredient adapter not initialized");
    }

    @Override
    public void onEnable() {
        MessageConfig.setup(this);
        saveDefaultConfig();
        ingredientItems = new IngredientItemAdapter(BuiltInIngredients.registry(), new IngredientMetadataCodec(this));

        AlchemyRuntimeSettings alchemySettings;
        MarketRuntimeSettings marketSettings;
        try {
            alchemySettings = AlchemyRuntimeSettings.from(getConfig());
            marketSettings = MarketRuntimeSettings.from(getConfig());
            marketQuotes = new MarketQuoteService(marketSettings.pricing());
        } catch (RuntimeException failure) {
            getLogger().severe("ArcaneBrewery configuration is invalid: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        java.util.concurrent.Executor async = command ->
                getServer().getScheduler().runTaskAsynchronously(this, command);

        try {
            storage = new SqliteStorageProvider(getDataFolder().toPath().resolve("arcanebrewery.db"));
            discoveries = new DiscoveryRuntime(
                    new DiscoveryRegistrationService(storage.discoveries()),
                    async
            );
            discoveryQueries = new DiscoveryQueryRuntime(storage.discoveries(), async);
            discoveryMutations = new DiscoveryMutationRuntime(storage.discoveries(), async);
            marketJournal = storage.marketJournal();
            var marketBottleReader = new CanonicalMarketBottleReader(
                    getName().toLowerCase(java.util.Locale.ROOT));
            marketAssets = new BukkitMarketAssetPort(marketBottleReader, storage.marketEscrow());
            int prepared = marketJournal.prepared(1000).size();
            int unresolvedEscrow = storage.marketEscrow().unresolved(1000).size();
            if (prepared > 0 || unresolvedEscrow > 0) {
                getLogger().warning("Market recovery required: " + prepared
                        + " prepared transaction(s), " + unresolvedEscrow
                        + " unresolved escrow record(s). No automatic payout/refund was attempted.");
            }
            getLogger().info("ArcaneBrewery storage ready (schema V" + storage.schemaVersion() + ").");
        } catch (RuntimeException failure) {
            getLogger().severe("ArcaneBrewery storage initialization failed: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = new OptionalArcaneEconomy(this);
        marketWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), task -> {
                    var thread = new Thread(task, "ArcaneBrewery-market");
                    thread.setDaemon(true);
                    return thread;
                });
        marketSettlements = new MarketSettlementRuntime(marketWorker,
                task -> getServer().getScheduler().runTask(this, task), getServer()::isPrimaryThread, System::currentTimeMillis);

        getServer().getPluginManager().registerEvents(new AgricultureListener(ingredientItems), this);
        getServer().getPluginManager().registerEvents(new CauldronWasherEngine(ingredientItems), this);
        getServer().getPluginManager().registerEvents(new DistilleryMenuHandler(this, ingredientItems), this);
        getServer().getPluginManager().registerEvents(new BreweryListener(this), this);
        alchemyMachines = new AlchemyMachineHandler(this, alchemySettings);
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

        var marketBottleReader = new CanonicalMarketBottleReader(
                getName().toLowerCase(java.util.Locale.ROOT));
        var marketPlayer = new BukkitMarketPlayerService(
                this, marketQuotes, marketBottleReader,
                () -> marketSettings.playerSalesEnabled() && economy != null && economy.isAvailable());
        getServer().getPluginManager().registerEvents(marketPlayer, this);

        var brew = java.util.Objects.requireNonNull(getCommand("brew"), "brew command missing from plugin.yml");
        var discoveryCommand = new DiscoveryCommand(
                this, discoveryQueries, discoveryMutations, consumption, marketPlayer);
        brew.setExecutor(discoveryCommand);
        brew.setTabCompleter(discoveryCommand);

        var brewMarket = java.util.Objects.requireNonNull(
                getCommand("brewmarket"), "brewmarket command missing from plugin.yml");
        var diagnostics = new MarketDiagnosticsCommand(
                this, marketJournal, storage.marketEscrow(), async);
        brewMarket.setExecutor(diagnostics);
        brewMarket.setTabCompleter(diagnostics);

        getLogger().info("ArcaneBrewery enabled.");
    }

    @Override
    public void onDisable() {
        if (marketSettlements != null) { marketSettlements.close(); marketSettlements = null; }
        if (economy != null) { economy.close(); economy = null; }
        if (marketWorker != null) { marketWorker.shutdownNow(); marketWorker = null; }
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
        marketQuotes = null;
        marketJournal = null;
        marketAssets = null;
        getLogger().info("ArcaneBrewery systems offline.");
    }
}
