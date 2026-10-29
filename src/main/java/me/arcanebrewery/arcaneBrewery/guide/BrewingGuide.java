package me.arcanebrewery.arcaneBrewery.guide;

import me.arcanebrewery.arcaneBrewery.MessageConfig;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.function.Consumer;

/** Read-only virtual reference inventories. No items are granted or machine data mutated. */
public final class BrewingGuide implements Listener {
    private static final int PAGE_SIZE = 36;
    private final Plugin plugin;
    private final GuideCatalog catalog;
    private final DiscoveryQueryRuntime queries;
    private final me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.MaterialDiscoveryListener materials;
    private final me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.MaterialCatalog materialCatalog;
    private final DiscoveryTextFormatter formatter = new DiscoveryTextFormatter();
    static final class View implements me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.MachineAutomationHolder {
        Inventory inventory;
        private final UUID player;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private final Map<Integer, Consumer<Player>> secondary = new HashMap<>();
        View(UUID player) { this.player = player; }
        @Override public Inventory getInventory() { return inventory; }
    }
    public BrewingGuide(Plugin plugin, GuideCatalog catalog, DiscoveryQueryRuntime queries) {
        this(plugin,catalog,queries,null);
    }
    public BrewingGuide(Plugin plugin,GuideCatalog catalog,DiscoveryQueryRuntime queries,me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.MaterialDiscoveryListener materials) {
        this.materials=materials;
        this.materialCatalog=materials!=null?materials.catalog():new me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.MaterialCatalog(
            new me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientItemAdapter(catalog.ingredientRegistry(),new me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientMetadataCodec("arcanebrewery")));
        this.plugin = Objects.requireNonNull(plugin);
        this.catalog = Objects.requireNonNull(catalog);
        this.queries = Objects.requireNonNull(queries);
    }
    private View create(Player player, String title) {
        View view = new View(player.getUniqueId());
        view.inventory = Bukkit.createInventory(view, 54, "§5§l" + title + "§r");
        button(view, 49, Material.BOOK, "Codex Home", List.of("Return to the main index."), this::open);
        button(view, 48, Material.BARRIER, "Close", List.of(), Player::closeInventory);
        return view;
    }
    public void inspect(Player player) {
        var held=player.getInventory().getItemInMainHand();
        var data=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata(plugin.getName().toLowerCase(Locale.ROOT));
        if(held.getType()!=Material.POTION || !held.hasItemMeta()) {
            player.sendMessage(MessageConfig.prefix()+"§eHold an elixir to inspect it.§r");return;
        }
        var meta=held.getItemMeta();
        try {
            var profile=data.readConsumption(meta);
            if(profile.isEmpty()) { player.sendMessage(MessageConfig.prefix()+"§eThis bottle has no supported elixir data.§r");return; }
            var q=profile.orElseThrow();
            List<GuideCatalog.Entry> entries=new ArrayList<>();
            List<String> summary=new ArrayList<>();
            summary.add("Quality: "+Math.round(q.quality())+"/100");
            if(data.producer(meta).isPresent()) summary.add("Brewed by "+data.producerLabel(meta));
            summary.add("Stage: "+(data.unfinished(meta)?"Unfinished — no discovery":"Finished"));
            data.wood(meta).ifPresent(w->summary.add("Wood: "+w));
            data.harvestSeconds(meta).ifPresent(t->summary.add("Harvested at: "+t+" seconds"));
            if(data.prematurePoison(meta)) summary.add("POISONOUS when consumed: withdrawn in the first 60 seconds.");
            entries.add(new GuideCatalog.Entry(Material.POTION,"Batch Summary",summary));
            entries.add(new GuideCatalog.Entry(Material.WRITABLE_BOOK,"Quality Breakdown",data.qualityBreakdown(meta)));
            List<String> effects=new ArrayList<>(List.of("Arcane load: "+Math.round(q.strength())+"/100",
                "Higher load increases temporary saturation.","Magical potency: "+Math.round(q.magicalPotency())+"/100",
                "Historical statistics retained"));
            data.readEffects(meta).ifPresent(p->effects.addAll(p.summary()));
            if(data.readEffects(meta).isEmpty()) data.readAlchemy(meta).ifPresent(a->{ effects.add("Effect: "+me.arcanebrewery.arcaneBrewery.application.consumption.PlayerConditionPolicy.readable(a.effect().name()));
                effects.add("Instability: "+Math.round(a.instability())+"/100"); });
            entries.add(new GuideCatalog.Entry(Material.AMETHYST_SHARD,"Effects & Safety",effects));
            entries(player,"Elixir Inspection",entries,0);
        } catch(IllegalArgumentException invalid) { player.sendMessage(MessageConfig.prefix()+"§cThis bottle has invalid metadata; it was retained.§r"); }
    }

    public void open(Player player) {
        View view = create(player, "Brewing Codex");
        button(view, 10, Material.CRAFTING_TABLE, "2. Machines & Controls", List.of("Build, operate and maintain your equipment."),
                this::machines);
        button(view, 12, Material.WHEAT, "Ingredients", List.of("Registry entries and actual washing availability."),
                p -> materials(p,me.arcanebrewery.arcaneBrewery.application.discovery.MaterialKey.Kind.INGREDIENT));
        button(view, 14, Material.KNOWLEDGE_BOOK, "Catalysts / Reagents", List.of("Supported items and magical affinities."),
                p -> materials(p,me.arcanebrewery.arcaneBrewery.application.discovery.MaterialKey.Kind.CATALYST));
        button(view, 16, Material.BOOK, "1. Start Brewing", List.of("Wash → extract → cultivate → mature → inspect."),
                p -> entries(p, "Starter Walkthroughs", catalog.starters(), 0));
        button(view, 28, Material.WRITABLE_BOOK, "Independent Recipes", List.of("Your independently discovered formulations."),
                p -> discoveries(p, false, 0));
        button(view, 30, Material.ENCHANTED_BOOK, "Public Discoveries", List.of("Public names; exact details still require access."),
                p -> discoveries(p, true, 0));
        button(view, 32, Material.KNOWLEDGE_BOOK, "Brewing Knowledge", List.of("Names, discovery, quality and magical conditions."),
                p -> entries(p, "Brewing Knowledge", catalog.knowledge(), 0));
        button(view, 40, Material.CHEST, "Legacy Compatibility Profiles", List.of("Older predefined profiles; not current starter recipes."),
                p -> entries(p,"Legacy Compatibility",List.of(new GuideCatalog.Entry(Material.CHEST,"Historical Bottles",List.of("The 30 legacy profiles remain compatible.","Hold an old bottle and use /brew inspect.","This page does not reveal locked materials or private recipes."))),0));
        player.openInventory(view.inventory);
    }
    private void machines(Player player) {
        View view = create(player, "Machines & Controls");
        var progress = me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.PlayerResearch.read(player);
        int slot = 10;
        for (var entry : catalog.machines()) {
            var type = specialist(entry.icon());
            boolean locked = type != null && !progress.earned().contains(type.name());
            if (locked) {
                button(view, slot, Material.BARRIER, "Locked · " + type.label,
                    List.of("§cPersonal unlock", "§eCost: " + type.levels + " XP levels", "",
                        "§7Spend levels once to unlock this machine.", "§7Construction materials are separate.",
                        "§aClick to unlock · right-click for build guide"), p -> {
                        try {
                            me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.PlayerResearch.purchase(p,type);
                            p.sendMessage("§aUnlocked permanently: " + type.label + " §7(−" + type.levels + " levels)");
                        } catch (RuntimeException failure) { p.sendMessage("§e" + failure.getMessage()); }
                        machines(p);
                    });
                view.secondary.put(slot, p -> machine(p, entry));
            } else button(view, slot, entry.icon(), entry.title(),
                List.of(type == null ? "§aAvailable" : "§aPermanently unlocked", "", "§eClick for structure and controls"),
                p -> machine(p, entry));
            slot++;
            if (slot == 17) slot = 19;
        }
        player.openInventory(view.inventory);
    }
    private static me.arcanebrewery.arcaneBrewery.application.research.SpecialistType specialist(Material icon) {
        return Arrays.stream(me.arcanebrewery.arcaneBrewery.application.research.SpecialistType.values())
            .filter(t -> t.core.equals(icon.name())).findFirst().orElse(null);
    }
    private void machine(Player player, GuideCatalog.Entry entry) {
        View view = create(player, entry.title());
        for (var part : MachineBuildLayout.parts(entry.icon()))
            view.inventory.setItem(part.slot(), item(part.icon(), part.title(), part.lines()));
        button(view, 32, Material.LEVER, "Controls & Operation", List.of("§eClick for step-by-step instructions"),
            p -> detail(p, entry, () -> machine(p, entry)));
        var type = specialist(entry.icon());
        if (type == me.arcanebrewery.arcaneBrewery.application.research.SpecialistType.REAGENT_PROCESSOR)
            button(view, 30, Material.PRISMARINE_SHARD, "Prepared Reagent Recipes", List.of("§eView exact ingredients and input slots"), this::processorRecipes);
        if (type != null) view.inventory.setItem(34, item(Material.CHEST,"Construction Materials",
            List.of("§e" + type.amountA + " × " + GuideCatalog.label(type.buildA.toLowerCase(Locale.ROOT)),
                "§e" + type.amountB + " × " + GuideCatalog.label(type.buildB.toLowerCase(Locale.ROOT)), "",
                "§7Put resources in the first two slots.", "§aClick Construct once.", "§7Then add processing inputs and Start.")));
        button(view, 45, Material.ARROW, "Back to Machines", List.of(), this::machines);
        player.openInventory(view.inventory);
    }
    private void processorRecipes(Player player) {
        View view = create(player, "Processor Recipes");
        var raw = List.of(Material.REDSTONE, Material.GLOWSTONE_DUST, Material.QUARTZ);
        var binder = List.of(Material.AMETHYST_SHARD, Material.BLAZE_ROD, Material.HONEYCOMB);
        var labels = List.of("Duration", "Potency", "Purification");
        for (int i = 0; i < 3; i++) {
            int row = 9 + i * 9;
            view.inventory.setItem(row + 2, item(raw.get(i), "4 × " + GuideCatalog.label(raw.get(i).name().toLowerCase(Locale.ROOT)),
                List.of("§bInput slot 1 · four consumed")));
            view.inventory.setItem(row + 4, item(binder.get(i), "1 × " + GuideCatalog.label(binder.get(i).name().toLowerCase(Locale.ROOT)),
                List.of("§bInput slot 2 · one consumed")));
            view.inventory.setItem(row + 6, item(Material.PRISMARINE_SHARD, "Prepared " + labels.get(i) + " Reagent",
                List.of("§aOutput slot 3 · one reagent", "§eClick Start · 10 seconds", "§7Construct the machine before processing.")));
        }
        button(view, 45, Material.ARROW, "Back to Processor", List.of(), p -> machine(p,
            catalog.machines().stream().filter(e -> e.icon() == Material.STONECUTTER).findFirst().orElseThrow()));
        player.openInventory(view.inventory);
    }
    private void materials(Player player,me.arcanebrewery.arcaneBrewery.application.discovery.MaterialKey.Kind kind) {
        View loading=create(player,"Material Knowledge");loading.inventory.setItem(22,item(Material.CLOCK,"Checking possession…",List.of()));player.openInventory(loading.inventory);
        var future=materials==null?java.util.concurrent.CompletableFuture.completedFuture(java.util.Set.<me.arcanebrewery.arcaneBrewery.application.discovery.MaterialKey>of()):materials.reconcile(player);
        future.whenComplete((known,failure)->onMain(()->{
            if(!current(player,loading))return;
            if(failure!=null){loading.inventory.setItem(22,item(Material.BARRIER,"Knowledge unavailable",List.of("Your progress was retained. Reopen to retry.")));return;}
            var projected=materialCatalog.project(kind,known);
            long unlocked=materialCatalog.entries().keySet().stream().filter(k->k.kind()==kind&&known.contains(k)).count();
            entries(player,(kind==me.arcanebrewery.arcaneBrewery.application.discovery.MaterialKey.Kind.INGREDIENT?"Ingredients":"Catalysts/Reagents")+" "+unlocked+"/"+projected.size(),projected,0);
        }));
    }
    private void entries(Player player, String title, List<GuideCatalog.Entry> entries, int page) {
        int maximum = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, maximum));
        View view = create(player, title + " — " + (current + 1));
        int start = current * PAGE_SIZE;
        for (int i = start; i < Math.min(entries.size(), start + PAGE_SIZE); i++) {
            var entry = entries.get(i);
            button(view, i - start, entry.icon(), entry.title(), entry.lines(),
                    p -> detail(p, entry, () -> entries(p, title, entries, current)));
        }
        if (current > 0) button(view, 45, Material.ARROW, "Previous", List.of(), p -> entries(p, title, entries, current - 1));
        if (current < maximum) button(view, 53, Material.ARROW, "Next", List.of(), p -> entries(p, title, entries, current + 1));
        player.openInventory(view.inventory);
    }
    private void detail(Player player, GuideCatalog.Entry entry, Runnable back) {
        View view = create(player, "Codex Details");
        view.inventory.setItem(4, item(entry.icon(), entry.title(), List.of()));
        for (int i = 0; i < entry.lines().size(); i++) {
            int slot = 10 + (i / 7) * 9 + (i % 7);
            if (slot >= 45) break;
            view.inventory.setItem(slot, item(Material.PAPER, "§bStep " + (i + 1), List.of(entry.lines().get(i))));
        }
        button(view, 45, Material.ARROW, "Back", List.of(), p -> back.run());
        player.openInventory(view.inventory);
    }
    private void discoveries(Player player, boolean publicList, int page) {
        if (page < 0 || page > (Integer.MAX_VALUE - PAGE_SIZE - 1) / PAGE_SIZE) return;
        int offset = page * PAGE_SIZE;
        View view = create(player, (publicList ? "Public Recipes" : "Independent Recipes") + " — " + (page + 1));
        view.inventory.setItem(22, item(Material.CLOCK, "Loading…", List.of("Fetching permitted discovery information.")));
        player.openInventory(view.inventory);
        // One extra result determines whether Next exists; no hard 200-discovery list ceiling.
        var future = publicList ? queries.publicDiscoveries(PAGE_SIZE + 1, offset)
                : queries.recipes(player.getUniqueId(), false, PAGE_SIZE + 1, offset).thenApply(items -> items.stream()
                    .map(d -> new RecipeListItem(d.displayName().value(), d.fingerprint())).toList());
        future.whenComplete((items, failure) -> onMain(() -> {
            if (!current(player, view)) return;
            view.inventory.setItem(22, null);
            if (failure != null) {
                plugin.getLogger().warning("Codex discovery query failed: " + failure.getClass().getSimpleName());
                view.inventory.setItem(22, item(Material.BARRIER, "Could not load discoveries", List.of("Return home and try again.")));
                return;
            }
            if (items.isEmpty()) view.inventory.setItem(22, item(Material.BOOK, "No discoveries here yet", List.of(
                    publicList ? "Players can choose to make discoveries public." : "Finish a mature batch to attempt discovery.",
                    "Starter walkthroughs are available from Codex Home.")));
            for (int i = 0; i < Math.min(items.size(), PAGE_SIZE); i++) {
                var recipe = items.get(i);
                button(view, i, Material.POTION, recipe.name(), List.of("ID: " + recipe.fingerprint().value().substring(0, 12),
                        "Click to inspect your current knowledge.", "One permanent rename: /brew rename <shown id> <name> confirm.", "Or hold your own bottle: /brew rename held <name>."),
                        p -> recipe(p, recipe.fingerprint(), () -> discoveries(p, publicList, page)));
            }
            if (page > 0) button(view, 45, Material.ARROW, "Previous", List.of(), p -> discoveries(p, publicList, page - 1));
            if (items.size() > PAGE_SIZE) button(view, 53, Material.ARROW, "Next", List.of(), p -> discoveries(p, publicList, page + 1));
        }));
    }
    public void recipe(Player player, String value) {
        if (!value.matches("[0-9a-fA-F]{64}")) {
            player.sendMessage(MessageConfig.prefix() + "§eUse a full 64-character recipe fingerprint. For short IDs use /brew recipe.§r");
            return;
        }
        recipe(player, new DrinkFingerprint(value.toLowerCase(Locale.ROOT)), () -> open(player));
    }
    private void recipe(Player player, DrinkFingerprint fingerprint, Runnable back) {
        View loading = create(player, "Recipe Knowledge");
        loading.inventory.setItem(22, item(Material.CLOCK, "Checking access…", List.of()));
        player.openInventory(loading.inventory);
        queries.knowledge(player.getUniqueId(), fingerprint).whenComplete((knowledge, failure) -> onMain(() -> {
            if (!current(player, loading)) return;
            if (failure != null) {
                plugin.getLogger().warning("Codex recipe query failed: " + failure.getClass().getSimpleName());
                loading.inventory.setItem(22, item(Material.BARRIER, "Could not load recipe", List.of("Return home and try again.")));
                return;
            }
            // Never read authoritative stored processes here: this is a recipient-specific projection.
            List<String> lines = new ArrayList<>();
            lines.add("Your knowledge: " + knowledge.state());
            lines.addAll(formatter.format(knowledge));
            lines.add("Rename your full personal record: /brew rename "+fingerprint.value().substring(0,12)+" <name> confirm (one permanent rename)");
            queries.info(player.getUniqueId(),fingerprint).whenComplete((info,infoFailure)->onMain(()->{
                if(!current(player,loading)) return;
                if(infoFailure==null) lines.add(info);
                detail(player,new GuideCatalog.Entry(Material.ENCHANTED_BOOK,"Recipe Knowledge",lines),back);
            }));
        }));
    }
    private boolean current(Player player, View view) {
        return player.isOnline() && player.getUniqueId().equals(view.player)
                && player.getOpenInventory().getTopInventory() == view.inventory;
    }
    private void onMain(Runnable action) {
        if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action);
    }
    private void button(View view, int slot, Material icon, String title, List<String> lore, Consumer<Player> action) {
        view.inventory.setItem(slot, item(icon, title, lore));
        view.actions.put(slot, action);
    }
    private ItemStack item(Material icon, String title, List<String> lore) {
        ItemStack item = new ItemStack(icon);
        var meta = Objects.requireNonNull(item.getItemMeta());
        me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems.mark(meta);
        meta.setDisplayName("§d§l" + title + "§r");
        meta.setLore(styledLore(lore));
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES);
        PresentationStyle.apply(meta);
        item.setItemMeta(meta);
        return item;
    }
    static List<String> styledLore(List<String> lines) {
        var styled = new ArrayList<String>();
        for (String line : lines) {
            if (line.isBlank()) { styled.add(""); continue; }
            String color = line.startsWith("§") ? "" : line.contains(":") ? "§b" : "§f";
            for (String wrapped : wrap(List.of(line))) styled.add(color + wrapped + "§r");
            styled.add("");
        }
        if (!styled.isEmpty() && styled.getLast().isEmpty()) styled.removeLast();
        return styled;
    }
    /** Hard wrap long fingerprints/IDs as well as prose so tooltips stay on screen. */
    static List<String> wrap(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String source : lines) {
            String line = source;
            while (line.length() > 48) {
                int split = line.lastIndexOf(' ', 48);
                if (split < 16) split = 48;
                result.add(line.substring(0, split));
                line = line.substring(split).stripLeading();
            }
            result.add(line);
        }
        return result;
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) return;
        event.setCancelled(true); // Includes bottom inventory, hotbar, offhand, double-click and creative gestures.
        if (!(event.getWhoClicked() instanceof Player player) || !current(player, view)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        Consumer<Player> action = event.getClick() == ClickType.RIGHT
            ? view.secondary.getOrDefault(event.getRawSlot(), view.actions.get(event.getRawSlot()))
            : view.actions.get(event.getRawSlot());
        if (action != null) onMain(() -> { if (current(player, view)) action.accept(player); });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof View) event.setCancelled(true);
    }
}
