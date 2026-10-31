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

    private static final Map<String,String> STOCK_MESSAGE_HASHES = Map.ofEntries(
            Map.entry("prefix","2d71ee593b0727f296b951d5433b0383f4bf07de7185e9a94c393ce718cb5af2"),
            Map.entry("structure_broken","88375a026dc95009e01852a008db948cf32bacc8d00d2fa8c549884a2d7862b6"),
            Map.entry("distillery_wash_first","2eff9d0b0a294fa54b4292e4928f02a71d4cad241ae9c3f48898269af5c319d8"),
            Map.entry("distillery_needs_catalyst","0c335c589ef3feb8a4f7dfdb7ff62268bd63817b5977506fd4c6504fbb6b9e85"),
            Map.entry("distillery_success","aaa676097a212feac279feca1416b07457a1e45d008b5e61a51f411e54d86340"),
            Map.entry("producer_reject","c9720252e82e0537a627aad9ae56f3ac78ddb6c49d7fc21778b41c79fbf134a5"),
            Map.entry("producer_reject_potion","5006ce81b47ccec68ee2e5524c75e933fa1236a69ed9f0952eee2b83d61b855a"),
            Map.entry("producer_incomplete","d815bf32dbe607382b780c5b2b55724136110beaf39ec1626d65c56763e9bb8c"),
            Map.entry("producer_success","134c5c6b3058f411f3cd8c9368c77f86c5d2ea034a8a4d87406a2ead3df684ec"));
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
            if (!target.contains(key) || isHistoricStock(key,target.getString(key))) target.set(key,value);
        });
        target.set("messages-version",2);
    }
    private static boolean isHistoricStock(String key,String value) {
        if(value==null) return false;
        try {
            String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return digest.equals(STOCK_MESSAGE_HASHES.get(key));
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
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
