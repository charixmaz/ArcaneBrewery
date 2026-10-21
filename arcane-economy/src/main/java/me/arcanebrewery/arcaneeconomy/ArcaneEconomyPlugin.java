package me.arcanebrewery.arcaneeconomy;

import me.arcanebrewery.arcaneeconomy.adapter.bukkit.SettingsLoader;
import me.arcanebrewery.arcaneeconomy.api.ArcaneEconomyService;
import me.arcanebrewery.arcaneeconomy.command.EconomyCommand;
import me.arcanebrewery.arcaneeconomy.domain.EconomySettings;
import me.arcanebrewery.arcaneeconomy.storage.SqliteLedger;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.Executor;

public final class ArcaneEconomyPlugin extends JavaPlugin {
    private SqliteLedger ledger;
    private EconomySettings settings;
    private Executor async;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            settings = SettingsLoader.load(getConfig(), getDataFolder().toPath());
            ledger = new SqliteLedger(
                    settings.sqliteFile(),
                    settings.money(),
                    settings.busyTimeoutMillis()
            );
        } catch (RuntimeException failure) {
            getLogger().severe("ArcaneEconomy startup failed: " + failure.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        async = command -> getServer().getScheduler().runTaskAsynchronously(this, command);
        getServer().getServicesManager().register(
                ArcaneEconomyService.class,
                ledger,
                this,
                ServicePriority.Normal
        );

        var handler = new EconomyCommand(this, ledger, settings, async);
        for (String command : new String[]{"money", "pay", "eco"}) {
            var pluginCommand = Objects.requireNonNull(getCommand(command), command + " missing from plugin.yml");
            pluginCommand.setExecutor(handler);
            pluginCommand.setTabCompleter(handler);
        }

        getLogger().info("ArcaneEconomy enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (ledger != null) {
            ledger.close();
            ledger = null;
        }
        getLogger().info("ArcaneEconomy disabled.");
    }
}
