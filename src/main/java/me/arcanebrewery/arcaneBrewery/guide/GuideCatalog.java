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
    public IngredientRegistry ingredientRegistry(){return ingredients;}
    private Entry entry(Material icon, String name, String... lines) { return new Entry(icon, name, List.of(lines)); }
    public List<Entry> machines() {
        return List.of(
            entry(Material.CAULDRON, "Ingredient Washing", "Fill a cauldron with water.",
                "Hold a supported ingredient; right-click.", "Each cycle washes one item and uses one water level.",
                "Repeat until its purity reaches 100%.", "See Ingredients for the seven washable materials."),
            entry(Material.BREWING_STAND, MessageConfig.text("names.extractor"),
                "Brewing Stand directly above a Crafting Table.", "Right-click either block to open.",
                "Slots 1–3: one bottle per slot.", "Slots 5–6: 1–8 washed ingredients each; all consumed.",
                "New batch duration: " + times.extractionMillis() / 1000.0 + " seconds.",
                "Produces Refined Brewing Bases; inputs lock while working."),
            entry(Material.BARREL, MessageConfig.text("names.cultivation"), "Use a plain Barrel, without a cask structure.",
                "Insert Refined Brewing Bases; close, then sneak-click the barrel.", "New batch duration: " + times.cultivationSeconds() + " seconds.",
                "Choose culture, temperature and duration; click Start Cultivation.", "Offline time counts; active batches keep their captured timing."),
            entry(Material.ENCHANTING_TABLE, MessageConfig.text("names.infuser"),
                "Enchanting Table with Crying Obsidian directly below.", "At least one Amethyst Block horizontally adjacent.",
                "Base + Catalyst I + optional Catalyst II; click Start Infusion.",
                "New operation duration: " + infusionSeconds + " seconds.",
                "Catalyst affinities are references, not guaranteed safe results.",
                "Integrity repair uses Iron Ingots; cleaning is not required."),
            entry(Material.OAK_STAIRS, MessageConfig.text("names.cask"),
                "Barrel with an adjacent sign: first line [Brewery].",
                "At least 12 matching wood components in the 3×3×3 area.",
                "Use matching planks, stairs, slabs or logs. Open with the sign.",
                "Four lanes; insert completed cultivated or infused bases.",
                "Maturation timing must be learned through experimentation.",
                "Before maturity: zero-quality Unfinished Elixir; no discovery.",
                "First 60s: poisonous. Severe over-aging ruins the batch.",
                "Mature withdrawal: finished elixir; process determines identity."),
            entry(Material.STONECUTTER,"Reagent Processor", "Personal research first: see Specialist Research.","Stonecutter above Copper Block. Sneak-right-click to claim.","Construction resources go in first two slots; click Construct.","Then process 4 raw grains + 1 binder into one prepared reagent in 10s.","Valid pairs: red grains + violet shard; luminous grains + fiery rod; pale mineral + honey harvest.","Outputs remain locked during processing; offline time counts."),
            entry(Material.LOOM,"Duration Infuser","Research first, then Loom above Redstone Block.","Construct with resources shown in Research.","One versioned finished elixir + one prepared duration reagent.","10s; utility can reach 5–10 minutes at tier I. Other families have lower caps.","One duration pass per bottle; no free reprocessing loops."),
            entry(Material.SMITHING_TABLE,"Essence Concentrator","Research first, then Smithing Table above Iron Block.","One versioned finished elixir + one prepared potency reagent.","10s; increase eligible tier, keep only 35% duration, within family caps.","Utility up to tier III / 90s, Strength II / 30s. Other effects retain conservative limits.","One concentration pass per bottle; exactly one input/output bottle."),
            entry(Material.GRINDSTONE,"Purification Chamber","Research first, then Grindstone above Amethyst Block.","One versioned finished elixir + one prepared purification reagent.","Click selector to choose an unwanted effect; Start consumes inputs.","10s; removes exactly the selected unwanted effect, once per bottle.","An absent/non-removable selection rejects without consumption."));
    }
    public List<Entry> ingredients() {
        return ingredients.definitions().values().stream().map(d -> {
            Material icon = Material.matchMaterial(d.id());
            if (icon == null) icon = Material.WHEAT;
            List<String> lines = new ArrayList<>();
            lines.add("ID: " + d.id());
            if (d.premium()) {
                lines.add("Special metadata ingredient; ordinary Wheat is different.");
                lines.add("Rare mature-Wheat harvest drop from the existing agriculture path.");
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
    /** Trusted catalog data; player pages must use MaterialCatalog's locked projection. */
    public List<Entry> catalysts() {
        return me.arcanebrewery.arcaneBrewery.application.alchemy.ReagentRegistry.all().stream().map(d->entry(
            Objects.requireNonNull(Material.matchMaterial(d.externalId())),d.materialName(),
            "Role: "+d.role().name().replace('_',' '),"Use in the Arcane Infuser; raw material, no washing required.",
            "Discover by obtaining it. Exact combinations require experimentation.",d.risky()?"Deliberately dangerous transformation.":"Inspect the resulting profile for actual effects.")).toList();
    }
    public List<Entry> starters() {
        return List.of(
            entry(Material.BOOK, "About Available Recipes", "The modern pipeline has no fixed named-elixir catalog.",
                "These are public production walkthroughs, not seeded discoveries.",
                "They do not grant items or pre-register discoveries; execution determines quality.",
                "Use My Discoveries and Public Discoveries for recorded recipes.",
                "Legacy Profiles documents older compatibility bottles separately.",
                "Inspect only your permitted records: /brew guide recipe <full fingerprint>."),
            starter(Material.WHEAT, "Plain Wheat Walkthrough", "Wheat", false),
            starter(Material.APPLE, "Plain Apple Walkthrough", "Apple", false),
            starter(Material.BOOK, "Experimenting with Infusion", "Wheat", true));
    }
    private Entry starter(Material icon, String title, String ingredient, boolean fire) {
        List<String> lines = new ArrayList<>(List.of("1. Wash one " + ingredient + " to 100% purity.",
            "2. Extract with one Glass Bottle: 78 C, Balanced cuts; click START.", "3. Insert the base in a plain Barrel; close, then sneak-click.",
            "4. Select WILD, 22 C / 60 s; click Start Cultivation and wait."));
        if (fire) lines.add("5. Obtain an active catalyst; its unlocked Codex entry explains the broad role. Experiment in the infuser.");
        lines.add((fire ? "6." : "5.") + " Mature in an Oak cask; choose a harvest time and record it.");
        lines.add("New batches use this server's current durations.");
        lines.add("Use a cask; compare harvest times to find your best batch.");
        lines.add("Temporary saturation and deliberately dangerous reactions still apply.");
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
                    "Legacy maturation timing must also be learned.",
                    "Record actual harvest times and compare resulting quality.",
                    "Wrong wood or overaging can ruin legacy quality.",
                    "This is not a complete current ingredient-to-elixir recipe.",
                    "The current extractor does not generate these legacy stages.",
                    "Legacy outputs do not register procedural discoveries."))
                .toList();
    }
    public List<Entry> knowledge() {
        return List.of(
            entry(Material.POTION,"Personal Names and Bottles","/brew rename held <name> confirm for a finished bottle brewed by you.",
                "/brew rename <short-id> <name> confirm for your independently discovered or fully learned record.",
                "One permanent rename per personal formulation; 1–80 visible characters, no control/format codes.",
                "Your name does not rename another producer's bottle.","Old bottles with no reliable producer remain unattributed."),
            entry(Material.WRITABLE_BOOK,"Formulation, Variant and Batch","Ingredients and relative proportions, culture and catalyst doses define discovery identity.",
                "Culture is the explicit finite style: WILD, LUMINOUS or QUIET.",
                "Scaled quantities, precise temperature/time, wood and random batch conditions do not create recipes.",
                "Exact process records are kept separately for reproducing particular batches.",
                "Different formulations do not guarantee different magical effects."),
            entry(Material.BOOK,"Personal Discovery and Server First","Players can independently discover the same formulation.",
                "Personal records are private by default. Reproduction updates personal best quality.",
                "The server first receives permanent recognition without publishing private instructions.",
                "Historical learned records retain their provenance and permissions.",
                "Exchange instructions outside the plugin; no sharing commands exist."),
            entry(Material.WHEAT,"Cultures and Controls","Built-in starter culture library: no external plugin or consumable required.",
                "WILD: ideal 22 C / 60 s; LUMINOUS: 28 C / 90 s, 1.2x potency.",
                "QUIET: 16 C / 120 s, 0.75x potency. Culture selection resets to its ideal.",
                "Temperature 10–36 C in 2 C steps; duration 30–300 s in 30 s steps.",
                "Extractor: 60–90 C in 2 C steps; ideal 78 C. Balanced, Wide or Clean cuts.",
                "Wide increases potency and toxicity risk; Clean produces a milder batch."),
            entry(Material.CLOCK,"Deterministic Quality","Workmanship weights: purity 20%, culture composition 5%, temperature 20%, duration 15%, extraction 15%,",
                "storage 10%, maturation 10%, catalyst stability 5%; dangerous materials/cuts penalize separately.",
                "100% reference: wash Wheat to 100%, 78 C Balanced extraction, WILD 22 C/60 s,",
                "no catalyst and suitable storage; discover the best harvest time experimentally.",
                "Mildness does not cap quality. Use /brew inspect for actual factor losses.",
                "Unfinished quality is zero; severe over-aging can also reduce quality to zero.",
                "Wood affects flavor; over-aging reduces quality. No random star roll denies perfection."),
            entry(Material.TRIPWIRE_HOOK,"Privacy and Safety","Short IDs resolve across your complete accessible catalog; ambiguous prefixes fail.",
                "Historical grants remain permission checked. Public names are not process permission.",
                "Owner-only machines, one active viewer. Automation blocked only for recognized machines.",
                "Saturation and dangerous alchemy still apply. First-60-second withdrawals remain poisonous.",
                "The 30 legacy profiles remain compatibility references, separate from new discoveries."));
    }
    public static String label(String id) {
        String value = id.startsWith("minecraft:") ? id.substring(10) : id;
        return value.replace('_', ' ');
    }
}
