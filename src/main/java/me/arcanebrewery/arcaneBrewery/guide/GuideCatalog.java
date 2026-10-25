package me.arcanebrewery.arcaneBrewery.guide;

import me.arcanebrewery.arcaneBrewery.MessageConfig;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ProcessingTimes;
import me.arcanebrewery.arcaneBrewery.application.ingredient.IngredientRegistry;
import me.arcanebrewery.arcaneBrewery.application.alchemy.BuiltInAlchemyCatalysts;
import org.bukkit.Material;
import java.util.*;

/** Public reference information only: never contains another player's process. */
public final class GuideCatalog {
    public record Entry(Material icon, String title, List<String> lines) {
        public Entry { lines = List.copyOf(lines); }
    }
    private final IngredientRegistry ingredients;
    private final ProcessingTimes times;
    private final double infusionSeconds;
    public GuideCatalog(IngredientRegistry ingredients, ProcessingTimes times, double infusionSeconds) {
        this.ingredients = Objects.requireNonNull(ingredients);
        this.times = Objects.requireNonNull(times);
        this.infusionSeconds = infusionSeconds;
    }
    private Entry entry(Material icon, String name, String... lines) { return new Entry(icon, name, List.of(lines)); }
    public List<Entry> machines() {
        return List.of(
            entry(Material.CAULDRON, "Ingredient Washing", "Fill a cauldron with water.",
                "Hold a supported ingredient; right-click.", "Each cycle washes one item and uses one water level.",
                "Repeat until its purity reaches 100%.", "See Ingredients for the seven washable materials."),
            entry(Material.BREWING_STAND, MessageConfig.text("names.extractor"),
                "Brewing Stand directly above a Crafting Table.", "Right-click either block to open.",
                "Slots 1–3: one bottle per slot.", "Slot 5: a washed supported ingredient.",
                "New batch duration: " + times.extractionMillis() / 1000.0 + " seconds.",
                "Produces Refined Brewing Bases; inputs lock while working."),
            entry(Material.BARREL, MessageConfig.text("names.cultivation"), "Use a plain Barrel, without a cask structure.",
                "Insert Refined Brewing Bases, then close it.", "New batch duration: " + times.cultivationSeconds() + " seconds.",
                "Wait for Cultivated Brewing Base before infusion or maturation.", "Offline time counts; active batches keep their captured timing."),
            entry(Material.ENCHANTING_TABLE, MessageConfig.text("names.infuser"),
                "Enchanting Table with Crying Obsidian directly below.", "At least one Amethyst Block horizontally adjacent.",
                "Base + Catalyst I + optional Catalyst II; click Start Infusion.",
                "New operation duration: " + infusionSeconds + " seconds.",
                "Catalyst affinities are references, not guaranteed safe results.",
                "Default maintenance uses Iron Ingots and Sponges; server settings may differ."),
            entry(Material.OAK_STAIRS, MessageConfig.text("names.cask"),
                "Barrel with an adjacent sign: first line [Brewery].",
                "At least 12 matching wood components in the 3×3×3 area.",
                "Use matching planks, stairs, slabs or logs. Open with the sign.",
                "Four lanes; insert completed cultivated or infused bases.",
                "New maturity target: " + times.maturationSeconds() + " seconds.",
                "Early withdrawal: Unfinished Elixir, no discovery.",
                "Mature withdrawal: finished elixir; process determines identity."),
            entry(Material.GRAY_CONCRETE, "Cleansing Producer (Legacy)",
                "Gray Concrete with an adjacent sign: first line [Producer].",
                "Accepts only the legacy refined industrial-base item.",
                "An ordinary Refined Brewing Base is NOT accepted.",
                "Four lanes, 10 seconds; shift-click a completed lane to collect.",
                "Produces Cleansing Solution for contaminated casks.",
                "Compatibility path; not a normal starter production step."));
    }
    public List<Entry> ingredients() {
        return ingredients.definitions().values().stream().map(d -> {
            Material icon = Material.matchMaterial(d.id());
            if (icon == null) icon = Material.WHEAT;
            List<String> lines = new ArrayList<>();
            lines.add("ID: " + d.id());
            if (d.premium()) {
                lines.add("Special metadata ingredient; ordinary Wheat is different.");
                lines.add("No normal acquisition recipe is provided by this guide.");
            } else if (me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.WashableIngredients.MATERIALS.contains(icon)) {
                lines.add("Starter-ready: washable in a water cauldron.");
                lines.add("Wash, then use in the Essence Extractor.");
            } else {
                lines.add("Registered ingredient; no ordinary washing path yet.");
                lines.add("Not advertised as a starter-ready extraction input.");
            }
            if (BuiltInAlchemyCatalysts.resolveExternal(d.id()).isPresent())
                lines.add("Also supported as an Arcane Infuser catalyst.");
            var p = d.profile();
            lines.add("Ingredient properties (0–100; not final elixir stats):");
            lines.add("Sugar " + p.sugar() + " | Starch " + p.starch());
            lines.add("Acidity " + p.acidity() + " | Bitterness " + p.bitterness());
            lines.add("Aroma " + p.aroma() + " | Toxicity " + p.toxicity());
            lines.add("Magical affinity " + p.magicalAffinity());
            return new Entry(icon, d.displayName(), lines);
        }).toList();
    }
    public List<Entry> catalysts() {
        return BuiltInAlchemyCatalysts.all().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> {
            String affinity = switch(e.getKey()) {
                case "minecraft:blaze_powder" -> "Fire Resistance";
                case "minecraft:amethyst_shard" -> "Invisibility";
                case "minecraft:glistering_melon_slice" -> "Regeneration";
                case "minecraft:glowstone_dust" -> "Night Vision";
                case "minecraft:shulker_shell" -> "Levitation";
                case "minecraft:chorus_fruit" -> "Teleportation";
                case "minecraft:netherite_scrap" -> "Curse (dangerous)";
                case "minecraft:wither_skeleton_skull" -> "Wither (dangerous)";
                default -> "Unknown";
            };
            return entry(Objects.requireNonNull(Material.matchMaterial(e.getKey())), e.getValue().displayName(),
                "Vanilla item: " + label(e.getKey()), "Affinity: " + affinity,
                "Use Catalyst I or optional Catalyst II in the Arcane Infuser.",
                "Requires a completed cultivated base; washing is not required for catalysts.",
                "Combined catalysts do not guarantee both effects.",
                "Quality, contamination and additive load influence the outcome.");
        }).toList();
    }
    public List<Entry> starters() {
        return List.of(
            entry(Material.BOOK, "About Available Recipes", "The modern pipeline has no fixed named-elixir catalog.",
                "These are public production walkthroughs, not seeded discoveries.",
                "They do not grant items, recipe ownership, or guaranteed quality.",
                "Use My Discoveries and Public Discoveries for recorded recipes.",
                "Legacy Profiles documents older compatibility bottles separately.",
                "To inspect a shared recipe: /brew guide recipe <full fingerprint>."),
            starter(Material.WHEAT, "Plain Wheat Walkthrough", "Wheat", false),
            starter(Material.APPLE, "Plain Apple Walkthrough", "Apple", false),
            starter(Material.BLAZE_POWDER, "Ember Infusion Walkthrough", "Wheat", true));
    }
    private Entry starter(Material icon, String title, String ingredient, boolean fire) {
        List<String> lines = new ArrayList<>(List.of("1. Wash one " + ingredient + " to 100% purity.",
            "2. Extract with one Glass Bottle.", "3. Cultivate the base in a plain Barrel; close it.",
            "4. Wait for cultivation to complete."));
        if (fire) lines.add("5. Infuse with one Blaze Powder; wait until complete.");
        lines.add((fire ? "6." : "5.") + " Mature in an Oak Maturation Cask until READY.");
        lines.add("New batches use this server's current durations.");
        lines.add("Record wood and withdrawal stage to reproduce the process.");
        lines.add("Saturation and contamination hazards still apply.");
        return new Entry(icon, title, lines);
    }
    public List<Entry> legacyProfiles() {
        Map<String, String> names = Map.ofEntries(
            Map.entry("WORT_BEER", "Barley Essence"), Map.entry("WORT_MEAD", "Meadow Essence"),
            Map.entry("WORT_GROG", "Sailor's Essence"), Map.entry("WORT_CIDER", "Orchard Essence"),
            Map.entry("WORT_PIT_BEER", "Miner's Essence"), Map.entry("WORT_TONIC", "Wandering Tonic"),
            Map.entry("WORT_CACTUS", "Badlands Essence"), Map.entry("WORT_MOONSHINE", "Marsh Essence"),
            Map.entry("WORT_TODDY", "Snowpeak Essence"), Map.entry("WORT_GLAZED_MEAD", "Honeyglaze Essence"),
            Map.entry("WORT_VODKA", "Glacial Essence"), Map.entry("PREMIUM_WORT_BEER", "Noble Grain Essence"),
            Map.entry("WORT_TEQUILA", "Cactus Essence"), Map.entry("WORT_PUMPKIN", "Golden Pumpkin Essence"),
            Map.entry("WORT_ABSINTHE", "Crimson Essence"), Map.entry("WORT_SAKI", "Bamboo Essence"),
            Map.entry("WORT_SCHNAPPS", "Glowberry Essence"), Map.entry("WORT_SLIME", "Mire Essence"),
            Map.entry("WORT_CHORUS", "Chorus Essence"), Map.entry("WORT_GIN", "Deepsea Essence"),
            Map.entry("WORT_WHISKEY", "Netherfire Essence"), Map.entry("WORT_BLINK", "Ender's Blink Essence"),
            Map.entry("WORT_GOLD", "Liquid Gold Essence"), Map.entry("WORT_BRANDY", "Dragonbreath Essence"),
            Map.entry("WORT_WITHER", "Wither Essence"), Map.entry("WORT_RUM", "Monument Essence"),
            Map.entry("WORT_AMETHYST", "Geode Essence"), Map.entry("WORT_DEBRIS", "Ancient Debris Essence"),
            Map.entry("WORT_WARDEN", "Soul Echo Essence"), Map.entry("WORT_SHULKER", "Gravity Essence"));
        return me.arcanebrewery.arcaneBrewery.BreweryData.RECIPES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).map(e -> entry(Material.POTION,
                    names.getOrDefault(e.getKey(), "Compatibility Essence") + " (Legacy)",
                    "Compatibility reference for older bottles only.",
                    "Fantasy reference label; older item names are retained.",
                    "Required wood: " + label(e.getValue().woodRequired.toLowerCase(Locale.ROOT)),
                    "Legacy target: " + e.getValue().baseDuration + " seconds.",
                    "Legacy harvest window: target through target + 15 seconds.",
                    "Wrong wood or overaging can ruin legacy quality.",
                    "This is not a complete current ingredient-to-elixir recipe.",
                    "The current extractor does not generate these legacy stages.",
                    "Legacy outputs do not register procedural discoveries."))
                .toList();
    }
    public List<Entry> knowledge() {
        return List.of(
            entry(Material.POTION, "Elixir Names", "New fallback names describe wood and the dominant ingredient.",
                "Example: Oak-aged Wheat Arcane Elixir.", "Different recipes may have the same descriptive name.",
                "The fingerprint identifies the recipe; the name is a label.",
                "Original discoverer can rename once: /brew rename <full id> <name>.",
                "Existing saved discovery names and custom names are preserved."),
            entry(Material.WRITABLE_BOOK, "How Discovery Works", "A mature canonical batch attempts discovery registration.",
                "The first discoverer owns that exact process; default visibility is private.",
                "Repeating an existing process is reproduction, not a new discovery.",
                "Ingredients/ratios, preparation, cultivation settings, extraction controls,",
                "catalyst doses, maturation wood and duration stage determine identity.",
                "Quality rolls, dirt and machine condition do not create new identities."),
            entry(Material.CLOCK, "How Many Elixirs?", "No gameplay discovery-count cap is configured.",
                "There is no honest fixed total for the current system.",
                "The engine supports more process settings than the current GUIs expose.",
                "Currently: one extraction ingredient slot, up to two catalyst inputs,",
                "wood choice, and distinct maturation duration stages.",
                "Default mature stages advance every 30 seconds.",
                "Same process at 121s and 126s: same recipe under default timing.",
                "More combinations do not necessarily mean more distinct magical effects."),
            entry(Material.SPIDER_EYE, "Quality, Contamination & Saturation", "Quality and contamination vary between batches of the same recipe.",
                "Preparation and safe machine condition matter.", "Elixirs cause temporary Arcane Saturation.",
                "Excess causes overload and recovery; contamination can poison.",
                "Dependence and withdrawal progression are off by default.",
                "Do not drink Cleansing Solution."),
            entry(Material.SPONGE, "Cleaning & Machine Care", "Infuser maintenance uses server-configured repair/cleaning materials.",
                "A contaminated cask uses Cleansing Solution on its cleaning control.",
                "Close the GUI, then use its sign to reboot.",
                "The cleansing producer is a legacy compatibility path.",
                "No new survival crafting recipe for Cleansing Solution is added here."),
            entry(Material.TRIPWIRE_HOOK, "Ownership & Safety", "First valid use claims a supported machine.",
                "Owner-only access; one viewer at a time.", "Hopper automation is blocked for recognized Brewery machines.",
                "An occupied machine cannot be removed safely until emptied.",
                "Progress and ownership persist across restarts.", "Admin bypass does not transfer ownership."));
    }
    public static String label(String id) {
        String value = id.startsWith("minecraft:") ? id.substring(10) : id;
        return value.replace('_', ' ');
    }
}
