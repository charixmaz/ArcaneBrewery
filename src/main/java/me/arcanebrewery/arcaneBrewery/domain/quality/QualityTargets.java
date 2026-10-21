package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

/** Recipe-specific optima, not universal real-world brewing constants. Durations are in seconds. */
public record QualityTargets(double fermentationTemperature, long fermentationSeconds,
        long agingSeconds, double storageTemperature, double temperatureTolerance) {
    public QualityTargets {
        fermentationTemperature = DomainValues.finite(fermentationTemperature);
        storageTemperature = DomainValues.finite(storageTemperature);
        temperatureTolerance = DomainValues.finite(temperatureTolerance);
        if (fermentationSeconds <= 0 || agingSeconds < 0 || temperatureTolerance <= 0)
            throw new IllegalArgumentException("invalid quality targets");
    }
}
