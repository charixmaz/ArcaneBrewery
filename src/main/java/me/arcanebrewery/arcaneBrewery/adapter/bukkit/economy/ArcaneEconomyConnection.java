package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneeconomy.api.ArcaneEconomyService;
import org.bukkit.event.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.Plugin;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** All ServicesManager/plugin inspection stays on the server thread. Workers
 * consume an atomically published binding, invalidated by lifecycle events. */
final class ArcaneEconomyConnection implements OptionalArcaneEconomy.Connection, Listener {
    private final Plugin plugin;
    private final AtomicReference<RegisteredServiceProvider<ArcaneEconomyService>> registration = new AtomicReference<>();
    private final ArcaneEconomyAdapter adapter;

    ArcaneEconomyConnection(Plugin plugin) {
        this.plugin = plugin;
        adapter = new ArcaneEconomyAdapter(() -> {
            var current = registration.get();
            return current == null ? null : current.getProvider();
        }, () -> !plugin.getServer().isPrimaryThread());
        refresh();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    private void refresh() {
        var current = plugin.getServer().getServicesManager().getRegistration(ArcaneEconomyService.class);
        if (current != null && current.getPlugin().isEnabled()
                && current.getPlugin().getName().equals("ArcaneEconomy")) registration.set(current);
        else registration.set(null);
    }
    @EventHandler public void onRegister(ServiceRegisterEvent event) {
        registered(event.getProvider());
    }
    @EventHandler public void onUnregister(ServiceUnregisterEvent event) {
        unregistered(event.getProvider());
    }
    void registered(RegisteredServiceProvider<?> provider) {
        if (provider.getService() == ArcaneEconomyService.class) refresh();
    }
    void unregistered(RegisteredServiceProvider<?> provider) {
        // Unregister events can occur before ServicesManager removes its entry.
        // Never immediately look up and accidentally republish that stale entry.
        var current = registration.get();
        if (current == provider) registration.compareAndSet(current, null);
    }
    public boolean canAfford(UUID player, BigDecimal amount) { return adapter.canAfford(player, amount); }
    public boolean deposit(UUID player, BigDecimal amount, UUID id) { return adapter.deposit(player, amount, id); }
    public boolean withdraw(UUID player, BigDecimal amount, UUID id) { return adapter.withdraw(player, amount, id); }
    public void close() { registration.set(null); HandlerList.unregisterAll(this); }
}
