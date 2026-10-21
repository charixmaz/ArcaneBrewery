package me.arcanebrewery.arcaneeconomy.command;

import me.arcanebrewery.arcaneeconomy.api.*;
import me.arcanebrewery.arcaneeconomy.domain.EconomySettings;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;

public final class EconomyCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final ArcaneEconomyService economy;
    private final EconomySettings settings;
    private final Executor async;

    public EconomyCommand(
            JavaPlugin plugin,
            ArcaneEconomyService economy,
            EconomySettings settings,
            Executor async) {
        this.plugin = Objects.requireNonNull(plugin);
        this.economy = Objects.requireNonNull(economy);
        this.settings = Objects.requireNonNull(settings);
        this.async = Objects.requireNonNull(async);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "money" -> money(sender, args);
            case "pay" -> pay(sender, args);
            case "eco" -> eco(sender, args);
            default -> false;
        };
    }

    private boolean money(CommandSender sender, String[] args) {
        final Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cConsole must specify an online player.");
                return true;
            }
            target = player;
        } else if (args.length == 1 && sender.hasPermission("arcaneeconomy.balance.others")) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cThat player is not online.");
                return true;
            }
        } else {
            sender.sendMessage("§cUsage: /money [player]");
            return true;
        }

        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        async.execute(() -> {
            try {
                BigDecimal balance = economy.balance(targetId);
                sync(() -> sender.sendMessage(
                        "§a" + targetName + " balance: §f" + format(balance)));
            } catch (RuntimeException failure) {
                failure(sender, failure);
            }
        });
        return true;
    }

    private boolean pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /pay.");
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /pay <player> <amount>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§cThat player is not online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage("§cYou cannot pay yourself.");
            return true;
        }

        BigDecimal amount = parseAmount(sender, args[1]);
        if (amount == null) return true;

        UUID from = player.getUniqueId();
        UUID to = target.getUniqueId();
        UUID transactionId = UUID.randomUUID();
        String targetName = target.getName();

        async.execute(() -> {
            try {
                EconomyResult result = economy.transfer(from, to, amount, transactionId);
                sync(() -> {
                    if (result.applied()) {
                        sender.sendMessage("§aPaid §f" + targetName + " §a" + format(amount) + ".");
                        target.sendMessage("§aReceived §f" + format(amount) + " §afrom §f" + player.getName() + "§a.");
                    } else {
                        sender.sendMessage("§cPayment failed: " + readable(result));
                    }
                });
            } catch (RuntimeException failure) {
                failure(sender, failure);
            }
        });
        return true;
    }

    private boolean eco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("arcaneeconomy.admin")) {
            sender.sendMessage("§cYou do not have permission.");
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage("§cUsage: /eco <give|take|set> <player> <amount>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!Set.of("give", "take", "set").contains(action)) {
            sender.sendMessage("§cUnknown action. Use give, take, or set.");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cThat player is not online.");
            return true;
        }

        BigDecimal amount = parseAmount(sender, args[2]);
        if (amount == null) return true;

        UUID playerId = target.getUniqueId();
        UUID transactionId = UUID.randomUUID();
        async.execute(() -> {
            try {
                EconomyResult result = switch (action) {
                    case "give" -> economy.deposit(playerId, amount, transactionId);
                    case "take" -> economy.withdraw(playerId, amount, transactionId);
                    case "set" -> economy.setBalance(playerId, amount, transactionId);
                    default -> throw new IllegalStateException(action);
                };
                sync(() -> {
                    if (result.applied()) {
                        sender.sendMessage("§aUpdated §f" + target.getName()
                                + "§a balance to §f" + format(result.balance()) + "§a.");
                    } else {
                        sender.sendMessage("§cEconomy update failed: " + readable(result));
                    }
                });
            } catch (RuntimeException failure) {
                failure(sender, failure);
            }
        });
        return true;
    }

    private BigDecimal parseAmount(CommandSender sender, String raw) {
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException failure) {
            sender.sendMessage("§cAmount must be a positive decimal number.");
            return null;
        }
    }

    private String readable(EconomyResult result) {
        return switch (result.status()) {
            case INSUFFICIENT_FUNDS -> "insufficient funds";
            case OUT_OF_RANGE -> "balance limit reached";
            case INVALID -> result.message().isBlank() ? "invalid amount" : result.message();
            case DUPLICATE -> "duplicate transaction";
            case APPLIED -> "applied";
        };
    }

    private String format(BigDecimal amount) {
        return settings.symbol() + amount.setScale(settings.money().scale()).toPlainString();
    }

    private void failure(CommandSender sender, RuntimeException failure) {
        plugin.getLogger().severe("Economy command failed: " + failure.getMessage());
        sync(() -> sender.sendMessage("§cEconomy storage operation failed."));
    }

    private void sync(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("eco") && args.length == 1) {
            return prefix(args[0], List.of("give", "take", "set"));
        }
        int playerArgument = command.getName().equalsIgnoreCase("eco") ? 2 : 1;
        if (args.length == playerArgument) {
            return prefix(args[args.length - 1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private List<String> prefix(String raw, List<String> choices) {
        String prefix = raw.toLowerCase(Locale.ROOT);
        return choices.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
