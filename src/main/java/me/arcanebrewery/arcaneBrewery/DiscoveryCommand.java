package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

public final class DiscoveryCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 8;

    private final JavaPlugin plugin;
    private final DiscoveryQueryRuntime queries;
    private final DiscoveryTextFormatter formatter = new DiscoveryTextFormatter();

    public DiscoveryCommand(JavaPlugin plugin, DiscoveryQueryRuntime queries) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        this.queries = java.util.Objects.requireNonNull(queries);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ArcaneBrewery discovery commands are player-only.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(player);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "discoveries" -> {
                discoveries(player, args);
                yield true;
            }
            case "recipe" -> {
                recipe(player, args);
                yield true;
            }
            default -> {
                help(player);
                yield true;
            }
        };
    }

    private void discoveries(Player player, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
                if (page < 1) throw new NumberFormatException();
            } catch (NumberFormatException failure) {
                player.sendMessage("§c[Brewery] Page must be a positive number.");
                return;
            }
        }
        int requestedPage = page;
        int offset;
        try {
            offset = Math.multiplyExact(page - 1, PAGE_SIZE);
        } catch (ArithmeticException failure) {
            player.sendMessage("§c[Brewery] Page is too large.");
            return;
        }

        queries.owned(player.getUniqueId(), PAGE_SIZE, offset)
                .whenComplete((items, failure) -> onMain(() -> {
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online == null) return;
                    if (failure != null) {
                        plugin.getLogger().warning("Discovery list query failed: " + failure.getMessage());
                        online.sendMessage("§c[Brewery] Could not load your discoveries.");
                        return;
                    }
                    online.sendMessage("§d§lArcane Discoveries §7— page " + requestedPage);
                    if (items.isEmpty()) {
                        online.sendMessage("§7No discoveries on this page.");
                        return;
                    }
                    for (OwnedDiscoveryView item : items) {
                        String shortFingerprint = item.fingerprint().value().substring(0, 12);
                        online.sendMessage("§f" + item.displayName().value()
                                + " §8[" + shortFingerprint + "…] §7" + item.visibility());
                    }
                    online.sendMessage("§7Use §f/brew recipe <full fingerprint> §7to inspect knowledge.");
                }));
    }

    private void recipe(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUsage: /brew recipe <fingerprint>");
            return;
        }
        final DrinkFingerprint fingerprint;
        try {
            fingerprint = new DrinkFingerprint(args[1]);
        } catch (IllegalArgumentException failure) {
            player.sendMessage("§c[Brewery] Invalid recipe fingerprint.");
            return;
        }

        queries.knowledge(player.getUniqueId(), fingerprint)
                .whenComplete((knowledge, failure) -> onMain(() -> {
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online == null) return;
                    if (failure != null) {
                        plugin.getLogger().warning("Recipe knowledge query failed: " + failure.getMessage());
                        online.sendMessage("§c[Brewery] Could not load recipe knowledge.");
                        return;
                    }
                    online.sendMessage("§d§lArcane Recipe");
                    for (String line : formatter.format(knowledge)) {
                        online.sendMessage("§7" + line);
                    }
                }));
    }

    private void help(Player player) {
        player.sendMessage("§d§lArcaneBrewery Recipes");
        player.sendMessage("§f/brew discoveries [page] §7— list recipes you discovered");
        player.sendMessage("§f/brew recipe <fingerprint> §7— inspect what you know");
    }

    private void onMain(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("discoveries", "recipe", "help").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
