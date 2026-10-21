package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.ArcaneBrewery;
import me.arcanebrewery.arcaneBrewery.application.economy.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class BukkitMarketPlayerService {
    private final ArcaneBrewery plugin;
    private final MarketQuoteService quotes;
    private final CanonicalMarketBottleReader bottles;
    private final BooleanSupplier salesAvailable;
    private final ConcurrentHashMap<UUID, PendingSale> pending = new ConcurrentHashMap<>();

    public BukkitMarketPlayerService(
            ArcaneBrewery plugin,
            MarketQuoteService quotes,
            CanonicalMarketBottleReader bottles,
            BooleanSupplier salesAvailable) {
        this.plugin = Objects.requireNonNull(plugin);
        this.quotes = Objects.requireNonNull(quotes);
        this.bottles = Objects.requireNonNull(bottles);
        this.salesAvailable = Objects.requireNonNull(salesAvailable);
    }

    public void value(Player player) {
        requireMainThread();
        var asset = bottles.read(player.getInventory().getItemInMainHand());
        if (asset.isEmpty()) {
            player.sendMessage("§c[Brewery] Hold one finished canonical drink bottle in your main hand.");
            return;
        }

        MarketQuote quote = quotes.quote(
                asset.get(), MarketSide.PLAYER_SELLS, System.currentTimeMillis(), UUID.randomUUID());
        player.sendMessage("§d§lArcane Market Value");
        player.sendMessage("§7Intrinsic value: §f" + format(asset.get().value().units()) + " units");
        player.sendMessage("§7Current server sell quote: §6" + format(quote.price()));
        if (!salesAvailable.getAsBoolean()) {
            player.sendMessage("§7Player selling is currently disabled or unavailable.");
        }
    }

    public void quoteSale(Player player) {
        requireMainThread();
        if (!salesAvailable.getAsBoolean()) {
            player.sendMessage("§c[Brewery] Player drink selling is currently disabled or unavailable.");
            return;
        }

        var asset = bottles.read(player.getInventory().getItemInMainHand());
        if (asset.isEmpty()) {
            player.sendMessage("§c[Brewery] Hold one finished canonical drink bottle in your main hand.");
            return;
        }

        long now = System.currentTimeMillis();
        MarketQuote quote = quotes.quote(asset.get(), MarketSide.PLAYER_SELLS, now, UUID.randomUUID());
        pending.put(player.getUniqueId(), new PendingSale(asset.get(), quote));

        long seconds = Math.max(1L, (quote.expiresAtEpochMillis() - now + 999L) / 1000L);
        player.sendMessage("§d§lArcane Market Quote");
        player.sendMessage("§7Sell price: §6" + format(quote.price()));
        player.sendMessage("§7Expires in: §f" + seconds + "s");
        player.sendMessage("§7Confirm with §f/brew sell confirm §7while holding the same drink.");
    }

    public void confirmSale(Player player) {
        requireMainThread();
        if (!salesAvailable.getAsBoolean()) {
            player.sendMessage("§c[Brewery] Player drink selling is disabled by the server.");
            return;
        }

        PendingSale sale = pending.remove(player.getUniqueId());
        if (sale == null) {
            player.sendMessage("§c[Brewery] You do not have an active sell quote. Use /brew sell first.");
            return;
        }

        long now = System.currentTimeMillis();
        if (sale.quote().expired(now)) {
            player.sendMessage("§c[Brewery] That sell quote expired. Request a new quote.");
            return;
        }

        var held = bottles.read(player.getInventory().getItemInMainHand());
        if (held.isEmpty() || !held.get().equals(sale.asset())) {
            player.sendMessage("§c[Brewery] Hold the same quoted drink in your main hand to confirm.");
            return;
        }

        UUID transactionId = UUID.randomUUID();
        player.sendMessage("§7[Brewery] Processing sale...");
        plugin.settleMarketSale(
                player.getUniqueId(), sale.asset(), sale.quote(), transactionId)
                .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online == null) return;

                    if (failure != null) {
                        plugin.getLogger().warning("Market sale " + transactionId
                                + " requires review: " + rootMessage(failure));
                        online.sendMessage("§c[Brewery] Sale outcome is uncertain. Transaction §f"
                                + shortId(transactionId) + "§c was preserved for admin reconciliation.");
                        return;
                    }

                    switch (result.status()) {
                        case COMMITTED -> online.sendMessage("§a[Brewery] Drink sold for §6"
                                + format(result.amount()) + "§a.");
                        case REJECTED -> online.sendMessage("§c[Brewery] Sale rejected: "
                                + safeReason(result.reason()));
                        case DUPLICATE -> online.sendMessage("§e[Brewery] This transaction was already processed.");
                    }
                }));
    }

    public void clear(UUID playerId) {
        pending.remove(playerId);
    }

    private static String safeReason(String reason) {
        if (reason == null || reason.isBlank()) return "transaction could not be completed";
        return reason.length() <= 160 ? reason : reason.substring(0, 160);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current != current.getCause()) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static String format(double amount) {
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("market player command must run on server thread");
    }

    private record PendingSale(MarketAsset asset, MarketQuote quote) {}
}
