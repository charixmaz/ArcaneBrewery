package me.arcanebrewery.arcaneBrewery;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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

        // Core Prefix Format
        addDefault("prefix", "§d§l§o[Arcane] §f§o");

        // General Errors
        addDefault("structure_broken", "⚠ Warning: Structural integrity compromised. Machine offline.");

        // Distillery Messages
        addDefault("distillery_wash_first", "Pipeline stalled! Raw elements clog the boiler. You must wash the ingredient first!");
        addDefault("distillery_needs_catalyst", "Critical error! Refining Ethanol Wort requires a Golden Apple catalyst.");
        addDefault("distillery_success", "✔ Condensation line cycle completed successfully.");

        // Producer Messages
        addDefault("producer_reject", "Lane Rejected! This factory only accepts boiled REFINED INDUSTRIAL ALCOHOL.");
        addDefault("producer_reject_potion", "Lane Rejected! This machine only accepts potion compounds.");
        addDefault("producer_incomplete", "Processing incomplete! Fluid condensation lines are still running.");
        addDefault("producer_success", "✔ Successfully collected 1x bottle of pure Industrial Methanol Fluid.");

        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }

    private static void addDefault(String path, String val) {
        if (!config.contains(path)) config.set(path, val);
    }

    public static String get(String path) {
        return config.getString("prefix") + config.getString(path);
    }
}