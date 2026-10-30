package me.arcanebrewery.arcaneBrewery;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public class MessageConfig {
    private static File file;
    private static FileConfiguration config;

    public static void setup(JavaPlugin plugin) {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (Exception e) { e.printStackTrace(); }
        }
        config = YamlConfiguration.loadConfiguration(file);

        migrate(config);

        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    private static void addDefault(String path, String val) {
        if (!config.contains(path)) config.set(path, val);
    }

    private static final Map<String,String> OLD = Map.of(
            "prefix","§d§l§o[Arcane] §f§o",
            "structure_broken","⚠ Warning: Structural integrity compromised. Machine offline.",
            "distillery_wash_first","Pipeline stalled! Raw elements clog the boiler. You must wash the ingredient first!",
            "distillery_needs_catalyst","Critical error! Refining Ethanol Wort requires a Golden Apple catalyst.",
            "distillery_success","✔ Condensation line cycle completed successfully.",
            "producer_reject","Lane Rejected! This factory only accepts boiled REFINED INDUSTRIAL ALCOHOL.",
            "producer_reject_potion","Lane Rejected! This machine only accepts potion compounds.",
            "producer_incomplete","Processing incomplete! Fluid condensation lines are still running.",
            "producer_success","✔ Successfully collected 1x bottle of pure Industrial Methanol Fluid.");
    private static final Map<String,String> DEFAULTS = Map.ofEntries(
            Map.entry("prefix","§d§l[ArcaneAlchemy]§r §f"),
            Map.entry("structure_broken","§eStructure incomplete. Contents and progress are retained.§r"),
            Map.entry("distillery_wash_first","§eWash this supported ingredient in a water cauldron first.§r"),
            Map.entry("distillery_needs_catalyst","§eThis legacy refinement requires a Golden Apple catalyst.§r"),
            Map.entry("distillery_success","§aExtraction complete. Your refined bases are ready.§r"),
            Map.entry("producer_reject","§eThis producer accepts only the legacy refined catalyst base.§r"),
            Map.entry("producer_reject_potion","§eThis machine accepts potion compounds only.§r"),
            Map.entry("producer_incomplete","§eProcessing is still running.§r"),
            Map.entry("producer_success","§aCollected one Cleansing Solution.§r"),
            Map.entry("names.extractor","Essence Extractor"),
            Map.entry("names.refined-base","Refined Brewing Base"),
            Map.entry("names.cultivation","Cultivation Barrel"),
            Map.entry("names.cultivated-base","Cultivated Brewing Base"),
            Map.entry("names.infuser","Arcane Infuser"),
            Map.entry("names.cask","Maturation Cask"),
            Map.entry("names.elixir","Finished Elixir"),
            Map.entry("names.unfinished","Unfinished Elixir"),
            Map.entry("names.cleanser","Cleansing Solution"));

    /** Replace only exact historic stock values; customized keys always win. */
    public static void migrate(FileConfiguration target) {
        if (target.getInt("messages-version",0)>2) throw new IllegalStateException("messages.yml uses a newer messages-version; file left unchanged");
        DEFAULTS.forEach((key,value) -> {
            if (!target.contains(key) || java.util.Objects.equals(target.getString(key),OLD.get(key))) target.set(key,value);
        });
        target.set("messages-version",2);
    }
    public static String text(String path) {
        return config == null ? DEFAULTS.getOrDefault(path,path) : config.getString(path,DEFAULTS.getOrDefault(path,path));
    }
    public static String title(String name) { return "§5§l" + text("names." + name) + "§r"; }
    public static String prefix() { return text("prefix").replace("ArcaneBrewery","ArcaneAlchemy"); }

    public static String get(String path) {
        return prefix() + text(path) + "§r";
    }
}
