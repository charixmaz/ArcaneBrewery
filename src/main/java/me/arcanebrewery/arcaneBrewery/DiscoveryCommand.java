package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class DiscoveryCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 8;

    private final JavaPlugin plugin;
    private final DiscoveryQueryRuntime queries;
    private final DiscoveryMutationRuntime mutations;
    private final ConsumptionListener consumption;
    private final DiscoveryTextFormatter formatter = new DiscoveryTextFormatter();

    public DiscoveryCommand(JavaPlugin plugin, DiscoveryQueryRuntime queries,
            DiscoveryMutationRuntime mutations, ConsumptionListener consumption) {
        this.plugin = Objects.requireNonNull(plugin);
        this.queries = Objects.requireNonNull(queries);
        this.mutations = Objects.requireNonNull(mutations);
        this.consumption = Objects.requireNonNull(consumption);
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
            case "discoveries" -> { discoveries(player, args); yield true; }
            case "recipe" -> { recipe(player, args); yield true; }
            case "rename" -> { rename(player, args); yield true; }
            case "visibility" -> { visibility(player, args); yield true; }
            case "share" -> { share(player, args); yield true; }
            case "revoke" -> { revoke(player, args); yield true; }
            case "status" -> { status(player); yield true; }
            default -> { help(player); yield true; }
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
                        plugin.getLogger().warning("Discovery list query failed: " + rootMessage(failure));
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
        DrinkFingerprint fingerprint = fingerprint(player, args[1]);
        if (fingerprint == null) return;

        queries.knowledge(player.getUniqueId(), fingerprint)
                .whenComplete((knowledge, failure) -> onMain(() -> {
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online == null) return;
                    if (failure != null) {
                        plugin.getLogger().warning("Recipe knowledge query failed: " + rootMessage(failure));
                        online.sendMessage("§c[Brewery] Could not load recipe knowledge.");
                        return;
                    }
                    online.sendMessage("§d§lArcane Recipe");
                    for (String line : formatter.format(knowledge)) online.sendMessage("§7" + line);
                }));
    }

    private void rename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /brew rename <fingerprint> <new name>");
            return;
        }
        DrinkFingerprint fingerprint = fingerprint(player, args[1]);
        if (fingerprint == null) return;
        String name = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        completeMutation(player, mutations.rename(player.getUniqueId(), fingerprint, name),
                result -> "§a[Brewery] Recipe renamed to §f" + result.displayName().value());
    }

    private void visibility(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUsage: /brew visibility <fingerprint> <private|shared|public>");
            return;
        }
        DrinkFingerprint fingerprint = fingerprint(player, args[1]);
        if (fingerprint == null) return;
        final DiscoveryVisibility visibility;
        try {
            visibility = DiscoveryVisibility.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            player.sendMessage("§c[Brewery] Visibility must be private, shared, or public.");
            return;
        }
        completeMutation(player, mutations.visibility(player.getUniqueId(), fingerprint, visibility),
                result -> "§a[Brewery] Visibility is now §f" + result.visibility());
    }

    private void share(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cUsage: /brew share <fingerprint> <player|uuid> <full|partial> [fields]");
            return;
        }
        DrinkFingerprint fingerprint = fingerprint(player, args[1]);
        if (fingerprint == null) return;
        UUID recipient = recipient(player, args[2]);
        if (recipient == null) return;

        if (args[3].equalsIgnoreCase("full")) {
            if (args.length != 4) {
                player.sendMessage("§cUsage: /brew share <fingerprint> <player|uuid> full");
                return;
            }
            completeMutation(player, mutations.shareFull(player.getUniqueId(), fingerprint, recipient),
                    result -> "§a[Brewery] Full recipe knowledge shared.");
            return;
        }

        if (!args[3].equalsIgnoreCase("partial") || args.length < 5) {
            player.sendMessage("§cUsage: /brew share <fingerprint> <player|uuid> partial <field,field,...>");
            return;
        }

        final Set<ProcessField> fields;
        try {
            fields = parseFields(Arrays.copyOfRange(args, 4, args.length));
        } catch (IllegalArgumentException failure) {
            player.sendMessage("§c[Brewery] " + failure.getMessage());
            return;
        }
        completeMutation(player, mutations.sharePartial(player.getUniqueId(), fingerprint, recipient, fields),
                result -> "§a[Brewery] Partial recipe knowledge shared.");
    }

    private void revoke(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUsage: /brew revoke <fingerprint> <player|uuid>");
            return;
        }
        DrinkFingerprint fingerprint = fingerprint(player, args[1]);
        if (fingerprint == null) return;
        UUID recipient = recipient(player, args[2]);
        if (recipient == null) return;

        completeMutation(player, mutations.revoke(player.getUniqueId(), fingerprint, recipient),
                result -> "§a[Brewery] Recipe access revoked.");
    }

    private void completeMutation(Player player,
            java.util.concurrent.CompletableFuture<DiscoveryMutationResult> future,
            java.util.function.Function<DiscoveryMutationResult, String> success) {
        UUID playerId = player.getUniqueId();
        future.whenComplete((result, failure) -> onMain(() -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) return;
            if (failure == null) {
                online.sendMessage(success.apply(result));
                return;
            }
            Throwable root = root(failure);
            if (root instanceof SecurityException) {
                online.sendMessage("§c[Brewery] Only the original discoverer may change this recipe.");
            } else if (root instanceof IllegalArgumentException || root instanceof IllegalStateException) {
                online.sendMessage("§c[Brewery] " + root.getMessage());
            } else {
                plugin.getLogger().warning("Discovery mutation failed: " + root.getMessage());
                online.sendMessage("§c[Brewery] Recipe update failed.");
            }
        }));
    }

    private DrinkFingerprint fingerprint(Player player, String value) {
        try {
            return new DrinkFingerprint(value);
        } catch (IllegalArgumentException failure) {
            player.sendMessage("§c[Brewery] Invalid recipe fingerprint.");
            return null;
        }
    }

    private UUID recipient(Player player, String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            Player online = Bukkit.getPlayerExact(value);
            if (online == null) {
                player.sendMessage("§c[Brewery] Player must be online or supplied by UUID.");
                return null;
            }
            return online.getUniqueId();
        }
    }

    private Set<ProcessField> parseFields(String[] tokens) {
        String joined = String.join(",", tokens);
        var fields = EnumSet.noneOf(ProcessField.class);
        for (String raw : joined.split(",")) {
            if (raw.isBlank()) continue;
            try {
                fields.add(ProcessField.valueOf(raw.strip().toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("Unknown recipe field: " + raw.strip());
            }
        }
        if (fields.isEmpty()) throw new IllegalArgumentException("At least one recipe field is required.");
        if (fields.size() == ProcessField.values().length)
            throw new IllegalArgumentException("Partial sharing must omit at least one recipe field.");
        return Set.copyOf(fields);
    }

    private void status(Player player) {
        var condition = consumption.refresh(player, false);
        var state = condition.state();
        player.sendMessage("§d§lArcaneBrewery Status");
        player.sendMessage("§7State: §f" + consumption.status(condition));
        player.sendMessage("§7Intoxication: §f" + Math.round(state.intoxication()) + "/100");
        player.sendMessage("§7Poisoning: §f" + Math.round(state.poisoning()) + "/100");
        player.sendMessage("§7Tolerance: §f" + Math.round(state.tolerance()) + "/100");
        player.sendMessage("§7Dependence: §f" + Math.round(state.dependence()) + "/100");
        player.sendMessage("§7Hangover: §f" + Math.round(condition.hangover()) + "/100");
        player.sendMessage("§7Withdrawal: §f" + Math.round(condition.withdrawal()) + "/100");
    }

    private void help(Player player) {
        player.sendMessage("§d§lArcaneBrewery Recipes");
        player.sendMessage("§f/brew discoveries [page] §7— list recipes you discovered");
        player.sendMessage("§f/brew recipe <fingerprint> §7— inspect what you know");
        player.sendMessage("§f/brew rename <fingerprint> <name> §7— one-time owner rename");
        player.sendMessage("§f/brew visibility <fingerprint> <private|shared|public>");
        player.sendMessage("§f/brew share <fingerprint> <player|uuid> <full|partial> [fields]");
        player.sendMessage("§f/brew revoke <fingerprint> <player|uuid>");
        player.sendMessage("§f/brew status §7— show intoxication and recovery state");
    }

    private void onMain(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private Throwable root(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current != current.getCause()) current = current.getCause();
        return current;
    }

    private String rootMessage(Throwable failure) {
        Throwable root = root(failure);
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("discoveries", "recipe", "rename", "visibility", "share", "revoke", "status", "help").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("visibility")) {
            return List.of("private", "shared", "public").stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("share")) {
            return List.of("full", "partial").stream()
                    .filter(value -> value.startsWith(args[3].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
