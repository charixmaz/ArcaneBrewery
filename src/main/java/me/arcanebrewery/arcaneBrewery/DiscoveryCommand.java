package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy.BukkitMarketPlayerService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class DiscoveryCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 8;
    public static final List<String> PLAYER_COMMANDS=List.of("inspect", "guide", "codex", "discoveries", "recipe", "rename", "visibility", "status", "value", "sell", "help");

    private final JavaPlugin plugin;
    private final DiscoveryQueryRuntime queries;
    private final DiscoveryMutationRuntime mutations;
    private final ConsumptionListener consumption;
    private final BukkitMarketPlayerService market;
    private final me.arcanebrewery.arcaneBrewery.guide.BrewingGuide guide;
    private final DiscoveryTextFormatter formatter = new DiscoveryTextFormatter();

    public DiscoveryCommand(JavaPlugin plugin, DiscoveryQueryRuntime queries,
            DiscoveryMutationRuntime mutations, ConsumptionListener consumption,
            BukkitMarketPlayerService market, me.arcanebrewery.arcaneBrewery.guide.BrewingGuide guide) {
        this.plugin = Objects.requireNonNull(plugin);
        this.queries = Objects.requireNonNull(queries);
        this.mutations = Objects.requireNonNull(mutations);
        this.consumption = Objects.requireNonNull(consumption);
        this.market = Objects.requireNonNull(market);
        this.guide = Objects.requireNonNull(guide);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("ArcaneBrewery discovery commands are player-only.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(player,args.length>=2?args[1]:"");
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "guide", "codex" -> {
                if (args.length == 1) guide.open(player);
                else if (args.length == 3 && args[1].equalsIgnoreCase("recipe")) guide.recipe(player, args[2]);
                else player.sendMessage(MessageConfig.prefix() + "§eUsage: /brew guide [recipe <full fingerprint>]§r");
                yield true;
            }
            case "inspect" -> { guide.inspect(player); yield true; }
            case "discoveries" -> { discoveries(player, args); yield true; }
            case "recipe" -> { recipe(player, args); yield true; }
            case "rename" -> { rename(player, args); yield true; }
            case "visibility" -> { visibility(player, args); yield true; }
            case "status" -> { status(player); yield true; }
            case "value" -> { market.value(player); yield true; }
            case "sell" -> {
                if (args.length == 1) market.quoteSale(player);
                else if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) market.confirmSale(player);
                else player.sendMessage("§cUsage: /brew sell [confirm]");
                yield true;
            }
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
                player.sendMessage("§c[ArcaneBrewery] Page must be a positive number.");
                return;
            }
        }
        int requestedPage = page;
        int offset;
        try {
            offset = Math.multiplyExact(page - 1, PAGE_SIZE);
        } catch (ArithmeticException failure) {
            player.sendMessage("§c[ArcaneBrewery] Page is too large.");
            return;
        }

        queries.owned(player.getUniqueId(), PAGE_SIZE, offset)
                .whenComplete((items, failure) -> onMain(() -> {
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online == null) return;
                    if (failure != null) {
                        plugin.getLogger().warning("Discovery list query failed: " + rootMessage(failure));
                        online.sendMessage("§c[ArcaneBrewery] Could not load your discoveries.");
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
                    online.sendMessage("§7Use §f/brew recipe <shown id> §7to inspect knowledge.");
                }));
    }

    private void recipe(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUsage: /brew recipe <fingerprint|shown id>");
            return;
        }

        String value = args[1].strip().toLowerCase(Locale.ROOT);
        if (value.length() == 64) {
            DrinkFingerprint fingerprint = fingerprint(player, value);
            if (fingerprint != null) showRecipe(player, fingerprint);
            return;
        }

        if (value.length() < 6 || !value.matches("[0-9a-f]+")) {
            player.sendMessage("§c[ArcaneBrewery] Invalid recipe id.");
            return;
        }

        resolve(player,value,id->showRecipe(player,id));
    }
    private void resolve(Player player,String prefix,java.util.function.Consumer<DrinkFingerprint> action) {
        UUID actor=player.getUniqueId();
        queries.resolve(actor,prefix).whenComplete((id,failure)->onMain(()->{
            Player online=Bukkit.getPlayer(actor);if(online==null) return;
            if(failure!=null) online.sendMessage(MessageConfig.prefix()+"§e"+rootMessage(failure)+"§r");
            else action.accept(id);
        }));
    }

    private void showRecipe(Player player, DrinkFingerprint fingerprint) {
        queries.knowledge(player.getUniqueId(), fingerprint)
                .whenComplete((knowledge, failure) -> onMain(() -> {
                    Player online = Bukkit.getPlayer(player.getUniqueId());
                    if (online == null) return;
                    if (failure != null) {
                        plugin.getLogger().warning("Recipe knowledge query failed: " + rootMessage(failure));
                        online.sendMessage("§c[ArcaneBrewery] Could not load recipe knowledge.");
                        return;
                    }
                    online.sendMessage("§d§lArcane Recipe");
                    for (String line : formatter.format(knowledge)) online.sendMessage("§7" + line);
                }));
    }

    private void rename(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /brew rename <held|short-id> <new name>");
            return;
        }
        boolean confirmed=args.length>=4 && args[args.length-1].equalsIgnoreCase("confirm");
        if(!confirmed){player.sendMessage("§eOne permanent rename per personal formulation. This cannot be undone. Repeat the command with 'confirm' at the end to accept.");return;}
        String name=String.join(" ",Arrays.copyOfRange(args,2,args.length-1));
        java.util.function.Consumer<DrinkFingerprint> rename=id->completeMutation(player,mutations.rename(player.getUniqueId(),id,name),
            result->{
                queries.invalidatePersonalNames(player.getUniqueId());
                new me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationRefreshListener(plugin,queries).refresh(player);
                return "§a[ArcaneBrewery] Your brewing record is now §f"+result.displayName().value();
            });
        if(args[1].equalsIgnoreCase("held")) {
            var held=player.getInventory().getItemInMainHand();
            var data=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata(plugin);
            if(!held.hasItemMeta() || !data.mayRenameHeld(held.getItemMeta(),player.getUniqueId())) {
                player.sendMessage(MessageConfig.prefix()+"§eHold a finished elixir with your own recorded producer UUID.§r");return;
            }
            rename.accept(data.formulation(held.getItemMeta()).orElseThrow());
        } else resolve(player,args[1],rename);
    }

    private void visibility(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§cUsage: /brew visibility <fingerprint> <private|public>");
            return;
        }
        DrinkFingerprint fingerprint = fingerprint(player, args[1]);
        if (fingerprint == null) return;
        final DiscoveryVisibility visibility;
        try {
            visibility = DiscoveryVisibility.valueOf(args[2].toUpperCase(Locale.ROOT));
            if(visibility==DiscoveryVisibility.SHARED) throw new IllegalArgumentException("Sharing removed");
        } catch (IllegalArgumentException failure) {
            player.sendMessage("§c[ArcaneBrewery] Visibility must be private or public.");
            return;
        }
        completeMutation(player, mutations.visibility(player.getUniqueId(), fingerprint, visibility),
                result -> "§a[ArcaneBrewery] Visibility is now §f" + result.visibility());
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
                online.sendMessage("§c[ArcaneBrewery] You need your own independently discovered or fully learned record.");
            } else if (root instanceof IllegalArgumentException || root instanceof IllegalStateException) {
                online.sendMessage("§c[ArcaneBrewery] " + root.getMessage());
            } else {
                plugin.getLogger().warning("Discovery mutation failed: " + root.getMessage());
                online.sendMessage("§c[ArcaneBrewery] Recipe update failed.");
            }
        }));
    }

    private DrinkFingerprint fingerprint(Player player, String value) {
        try {
            return new DrinkFingerprint(value);
        } catch (IllegalArgumentException failure) {
            player.sendMessage("§c[ArcaneBrewery] Invalid recipe fingerprint.");
            return null;
        }
    }

    private void status(Player player) {
        var condition = consumption.refresh(player, false);
        var state = condition.state();
        player.sendMessage("§d§lArcaneBrewery Status");
        player.sendMessage("§7State: §f" + consumption.status(condition));
        player.sendMessage("§7"+(consumption.legacyEnabled()?"Intoxication":"Arcane Saturation")+": §b" + Math.round(state.intoxication()) + "/100§r");
        player.sendMessage("§7Recovery: §f" + Math.round(condition.hangover()) + "/100§r");
        if (consumption.legacyEnabled()) {
            player.sendMessage("§7Legacy Tolerance: §f"+Math.round(state.tolerance())+"/100§r");
            player.sendMessage("§7Legacy Dependence: §f"+Math.round(state.dependence())+"/100§r");
            player.sendMessage("§7Legacy Withdrawal: §f"+Math.round(condition.withdrawal())+"/100§r");
        }
    }

    private void help(Player player) {
        help(player,"");
    }
    private void help(Player player,String topic) { BrewHelp.lines(topic).forEach(player::sendMessage); }

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
            return PLAYER_COMMANDS.stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("guide") || args[0].equalsIgnoreCase("codex"))) {
            return List.of("recipe").stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return List.of("confirm").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length==2 && args[0].equalsIgnoreCase("help")) return BrewHelp.TOPICS.stream()
                .filter(topic -> topic.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("visibility")) {
            return List.of("private", "public").stream()
                    .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if(args.length==2 && args[0].equalsIgnoreCase("rename")) return List.of("held");
        return List.of();
    }
}
