package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.Executor;

public final class MarketDiagnosticsCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final RecoverableMarketTransactionJournal journal;
    private final MarketEscrowStore escrow;
    private final Executor async;

    public MarketDiagnosticsCommand(
            JavaPlugin plugin,
            RecoverableMarketTransactionJournal journal,
            MarketEscrowStore escrow,
            Executor async) {
        this.plugin = Objects.requireNonNull(plugin);
        this.journal = Objects.requireNonNull(journal);
        this.escrow = Objects.requireNonNull(escrow);
        this.async = Objects.requireNonNull(async);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("arcanebrewery.admin.market")) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pending" -> { pending(sender, args); yield true; }
            case "tx" -> { transaction(sender, args); yield true; }
            default -> { help(sender); yield true; }
        };
    }

    private void pending(CommandSender sender, String[] args) {
        int limit = parseLimit(sender, args, 25);
        if (limit < 0) return;
        async.execute(() -> {
            try {
                var transactions = journal.prepared(limit);
                var unresolved = escrow.unresolved(limit);
                onMain(() -> {
                    sender.sendMessage("§d§lArcane Market Recovery");
                    sender.sendMessage("§7Prepared transactions: §f" + transactions.size());
                    for (var entry : transactions) {
                        var escrowEntry = escrowState(unresolved, entry.transactionId());
                        sender.sendMessage("§8- §f" + shortId(entry.transactionId())
                                + " §7player=" + shortId(entry.playerId())
                                + " price=" + String.format(Locale.ROOT, "%.2f", entry.quote().price())
                                + " escrow=" + escrowEntry);
                    }
                    sender.sendMessage("§7Unresolved escrow records: §f" + unresolved.size());
                    if (transactions.isEmpty() && unresolved.isEmpty()) {
                        sender.sendMessage("§aNo unresolved market recovery records.");
                    }
                });
            } catch (RuntimeException failure) {
                fail(sender, "Market recovery query failed", failure);
            }
        });
    }

    private void transaction(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /brewmarket tx <transaction-uuid>");
            return;
        }
        final UUID id;
        try {
            id = UUID.fromString(args[1]);
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage("§cTransaction ID must be a UUID.");
            return;
        }

        async.execute(() -> {
            try {
                var tx = journal.find(id);
                var item = escrow.find(id);
                onMain(() -> {
                    sender.sendMessage("§d§lArcane Market Transaction §f" + shortId(id));
                    if (tx.isEmpty()) {
                        sender.sendMessage("§cNo journal record exists.");
                    } else {
                        var entry = tx.get();
                        sender.sendMessage("§7Status: §f" + entry.status());
                        sender.sendMessage("§7Player: §f" + entry.playerId());
                        sender.sendMessage("§7Quote ID: §f" + entry.quote().quoteId());
                        sender.sendMessage("§7Fingerprint: §f" + entry.quote().fingerprint());
                        sender.sendMessage("§7Asset token: §f" + entry.quote().assetToken());
                        sender.sendMessage("§7Price: §6" + String.format(Locale.ROOT, "%.2f", entry.quote().price()));
                        if (!entry.reason().isBlank()) sender.sendMessage("§7Reason: §f" + entry.reason());
                    }
                    if (item.isEmpty()) {
                        sender.sendMessage("§7Escrow: §8none");
                    } else {
                        var entry = item.get();
                        sender.sendMessage("§7Escrow: §f" + entry.state()
                                + " §7slot=" + entry.inventorySlot()
                                + " bytes=" + entry.itemBytes().length);
                    }
                });
            } catch (RuntimeException failure) {
                fail(sender, "Market transaction query failed", failure);
            }
        });
    }

    private int parseLimit(CommandSender sender, String[] args, int fallback) {
        if (args.length == 1) return fallback;
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /brewmarket pending [1-100]");
            return -1;
        }
        try {
            int value = Integer.parseInt(args[1]);
            if (value < 1 || value > 100) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            sender.sendMessage("§cLimit must be from 1 to 100.");
            return -1;
        }
    }

    private String escrowState(List<MarketEscrowEntry> entries, UUID id) {
        return entries.stream()
                .filter(entry -> entry.transactionId().equals(id))
                .findFirst()
                .map(entry -> entry.state().name())
                .orElse("NONE");
    }

    private void help(CommandSender sender) {
        sender.sendMessage("§d§lArcane Market Admin");
        sender.sendMessage("§f/brewmarket pending [limit] §7— inspect unresolved records");
        sender.sendMessage("§f/brewmarket tx <uuid> §7— inspect one transaction and escrow");
        sender.sendMessage("§7V1.21 diagnostics are read-only; no automatic payout/refund.");
    }

    private void fail(CommandSender sender, String context, RuntimeException failure) {
        plugin.getLogger().warning(context + ": " + failure.getMessage());
        onMain(() -> sender.sendMessage("§c[Brewery] " + context + "."));
    }

    private void onMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("pending", "tx", "help").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("pending")) {
            return List.of("10", "25", "50", "100").stream()
                    .filter(value -> value.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
