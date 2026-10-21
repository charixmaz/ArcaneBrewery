package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneeconomy.api.ArcaneEconomyService;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.event.server.*;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class OptionalArcaneEconomyTest {
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, java.util.function.BiFunction<String,Object[],Object> methods) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,args) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == args[0];
            if (m.getName().equals("toString")) return type.getSimpleName();
            return methods.apply(m.getName(),args);
        });
    }
    static final class Environment {
        final AtomicBoolean enabled = new AtomicBoolean();
        final AtomicBoolean main = new AtomicBoolean();
        final AtomicReference<RegisteredServiceProvider<ArcaneEconomyService>> registration = new AtomicReference<>();
        final List<Listener> listeners = new ArrayList<>();
        final Server server;
        final Plugin owner;
        final Plugin provider;
        Environment() {
            var services = proxy(ServicesManager.class,(name,args) -> name.equals("getRegistration") ? registration.get() : null);
            var manager = proxy(PluginManager.class,(name,args) -> {
                if (name.equals("registerEvents")) { listeners.add((Listener) args[0]); return null; }
                if (name.equals("isPluginEnabled")) return enabled.get();
                return null;
            });
            server = proxy(Server.class,(name,args) -> switch (name) {
                case "getServicesManager" -> services;
                case "getPluginManager" -> manager;
                case "isPrimaryThread" -> main.get();
                default -> null;
            });
            owner = plugin("ArcaneBrewery"); provider = plugin("ArcaneEconomy");
        }
        Plugin plugin(String pluginName) {
            return proxy(Plugin.class,(name,args) -> switch (name) {
                case "getName" -> pluginName;
                case "getServer" -> server;
                case "isEnabled" -> enabled.get();
                case "getLogger" -> Logger.getLogger("economy-test");
                default -> null;
            });
        }
        RegisteredServiceProvider<ArcaneEconomyService> register(ArcaneEconomyService service) {
            var value = new RegisteredServiceProvider<>(ArcaneEconomyService.class,service,ServicePriority.Normal,provider);
            registration.set(value); return value;
        }
        ArcaneEconomyConnection connection() {
            return listeners.stream().filter(ArcaneEconomyConnection.class::isInstance)
                    .map(ArcaneEconomyConnection.class::cast).reduce((first,last) -> last).orElseThrow();
        }
    }
    @Test void absenceAndLateProviderEnableDoNotAffectBrewingStartup() {
        var env = new Environment();
        try (var economy = new OptionalArcaneEconomy(env.owner)) {
            assertFalse(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
            assertEquals(1,env.listeners.size());
            var ledger = new ArcaneEconomyAdapterTest.Ledger(); env.enabled.set(true); env.register(ledger);
            economy.providerEnabled();
            assertTrue(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        }
    }
    @Test void serviceUnregistrationBeforeRemovalImmediatelyInvalidatesBinding() {
        var env = new Environment(); env.enabled.set(true);
        var ledger = new ArcaneEconomyAdapterTest.Ledger(); var registered = env.register(ledger);
        try (var economy = new OptionalArcaneEconomy(env.owner)) {
            assertTrue(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
            env.connection().unregistered(registered);
            assertFalse(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
            var next = env.register(new ArcaneEconomyAdapterTest.Ledger());
            env.connection().registered(next);
            assertTrue(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
            assertEquals(1,ledger.calls);
        }
    }
    @Test void disabledMissingServiceAndReenableFailClosedUntilRegistration() {
        var env = new Environment(); env.enabled.set(true);
        try (var economy = new OptionalArcaneEconomy(env.owner)) {
            assertFalse(economy.canAfford(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE));
            var registered = env.register(new ArcaneEconomyAdapterTest.Ledger());
            env.connection().registered(registered);
            env.main.set(true);
            assertFalse(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
            env.main.set(false);
            economy.providerDisabled();
            env.enabled.set(false);
            assertFalse(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
            env.enabled.set(true); env.registration.set(null);
            economy.providerEnabled();
            assertFalse(economy.canAfford(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE));
            env.connection().registered(env.register(new ArcaneEconomyAdapterTest.Ledger()));
            assertTrue(economy.deposit(ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
        }
    }
    @Test void optionalBootstrapActuallyInstantiatesWhenApiClassesCannotBeLoaded() throws Exception {
        var location = OptionalArcaneEconomy.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new URLClassLoader(new java.net.URL[]{location}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("me.arcanebrewery.arcaneeconomy.")) throw new ClassNotFoundException(name);
                if (name.startsWith("me.arcanebrewery.arcaneBrewery.")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> found = findLoadedClass(name);
                        if (found == null) found = findClass(name);
                        if (resolve) resolveClass(found);
                        return found;
                    }
                }
                return super.loadClass(name,resolve);
            }
        }) {
            var env = new Environment();
            var type = loader.loadClass(OptionalArcaneEconomy.class.getName());
            var instance = type.getConstructor(Plugin.class).newInstance(env.owner);
            try {
                assertEquals(false,type.getMethod("deposit",UUID.class,BigDecimal.class,UUID.class)
                        .invoke(instance,ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE,UUID.randomUUID()));
                assertEquals(false,type.getMethod("canAfford",UUID.class,BigDecimal.class)
                        .invoke(instance,ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE));
            } finally { ((AutoCloseable) instance).close(); }
            env.enabled.set(true); // Installed but incompatible/missing public API.
            try (var incompatible = (AutoCloseable) type.getConstructor(Plugin.class).newInstance(env.owner)) {
                assertEquals(false,type.getMethod("canAfford",UUID.class,BigDecimal.class)
                        .invoke(incompatible,ArcaneEconomyAdapterTest.PLAYER,BigDecimal.ONE));
            }
        }
    }
}
