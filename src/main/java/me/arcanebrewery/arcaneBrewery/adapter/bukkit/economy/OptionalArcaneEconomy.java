package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import org.bukkit.event.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.Plugin;
import java.math.BigDecimal;
import java.util.UUID;

/** Safe to load with NO API classes installed. Only the isolated connection
 * references the optional provider's public API. No reflective API access. */
public final class OptionalArcaneEconomy implements EconomyPort, Listener, AutoCloseable {
    interface Connection extends EconomyPort, AutoCloseable { void close(); }
    private final Plugin plugin;
    private volatile EconomyPort delegate = UnavailableEconomyPort.INSTANCE;
    private Connection connection;
    private boolean closed;

    public OptionalArcaneEconomy(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        connect();
    }
    private void connect() {
        if (closed || connection != null || !plugin.getServer().getPluginManager().isPluginEnabled("ArcaneEconomy")) return;
        try {
            connection = new ArcaneEconomyConnection(plugin);
            delegate = connection;
        } catch (RuntimeException | LinkageError incompatible) {
            plugin.getLogger().warning("ArcaneEconomy integration unavailable; brewing remains enabled.");
        }
    }
    @EventHandler public void onEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("ArcaneEconomy")) providerEnabled();
    }
    @EventHandler public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().getName().equals("ArcaneEconomy")) providerDisabled();
    }
    void providerEnabled() { connect(); }
    void providerDisabled() { disconnect(); }
    private void disconnect() {
        delegate = UnavailableEconomyPort.INSTANCE;
        if (connection != null) { connection.close(); connection = null; }
    }
    public boolean isAvailable() { return delegate != UnavailableEconomyPort.INSTANCE; }
    public boolean canAfford(UUID player, BigDecimal amount) { return delegate.canAfford(player, amount); }
    public boolean deposit(UUID player, BigDecimal amount, UUID id) { return delegate.deposit(player, amount, id); }
    public boolean withdraw(UUID player, BigDecimal amount, UUID id) { return delegate.withdraw(player, amount, id); }
    public void close() { closed = true; disconnect(); HandlerList.unregisterAll(this); }
}
