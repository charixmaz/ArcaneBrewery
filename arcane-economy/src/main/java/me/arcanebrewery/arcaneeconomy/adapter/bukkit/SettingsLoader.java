package me.arcanebrewery.arcaneeconomy.adapter.bukkit;

import me.arcanebrewery.arcaneeconomy.domain.EconomySettings;
import org.bukkit.configuration.file.FileConfiguration;

import java.nio.file.Path;
import java.util.Objects;

public final class SettingsLoader {
    private SettingsLoader() {}

    public static EconomySettings load(FileConfiguration config, Path dataFolder) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(dataFolder);

        int scale = config.getInt("currency.scale", 2);
        var policy = EconomySettings.policy(
                scale,
                config.getString("currency.starting-balance", "0.00"),
                config.getString("currency.minimum-balance", "0.00"),
                config.getString("currency.maximum-balance", "999999999999.99")
        );

        String fileName = config.getString("storage.sqlite-file", "economy.db");
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\"))
            throw new IllegalArgumentException("storage.sqlite-file must be a simple filename");

        return new EconomySettings(
                config.getString("currency.name-singular", "coin"),
                config.getString("currency.name-plural", "coins"),
                config.getString("currency.symbol", "$"),
                policy,
                dataFolder.resolve(fileName),
                config.getInt("storage.busy-timeout-ms", 5000)
        );
    }
}
