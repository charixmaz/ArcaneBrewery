package me.arcanebrewery.arcaneBrewery.adapter.bukkit;

import org.bukkit.configuration.ConfigurationSection;

/** New batches capture these durations. Invalid configuration disables startup. */
public record ProcessingTimes(long extractionMillis, long cultivationSeconds, long maturationSeconds) {
    public static ProcessingTimes from(ConfigurationSection config) {
        return new ProcessingTimes(milliseconds(config,"processing.extraction-seconds",10),
                seconds(config,"processing.cultivation-seconds",60), seconds(config,"processing.maturation-seconds",120));
    }
    public ProcessingTimes {
        if (extractionMillis < 1 || extractionMillis > 604_800_000L
                || cultivationSeconds < 1 || cultivationSeconds > 604_800
                || maturationSeconds < 1 || maturationSeconds > 604_800)
            throw new IllegalArgumentException("processing durations must be positive and at most seven days");
    }
    private static double value(ConfigurationSection config,String key,double fallback) {
        Object raw = config.get(key,fallback);
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() <= 0 || number.doubleValue() > 604_800)
            throw new IllegalArgumentException(key + " must be a finite number > 0 and <= 604800 seconds");
        return number.doubleValue();
    }
    private static long milliseconds(ConfigurationSection config,String key,double fallback) {
        double value = value(config,key,fallback);
        if (value < 0.001) throw new IllegalArgumentException(key + " must be at least 0.001 seconds");
        return Math.round(value * 1000);
    }
    private static long seconds(ConfigurationSection config,String key,double fallback) {
        double value = value(config,key,fallback);
        if (value != Math.rint(value)) throw new IllegalArgumentException(key + " must use whole seconds");
        return (long)value;
    }
}
