package me.arcanebrewery.arcaneBrewery.application.aging;

import me.arcanebrewery.arcaneBrewery.domain.process.AgingProfile;
import java.util.Locale;
import java.util.Objects;

public record AgingSettings(
        String woodType,
        double oxidation,
        double storageTemperatureCelsius,
        double caskCleanliness
) {
    public AgingSettings {
        woodType = Objects.requireNonNull(woodType, "woodType").strip().toLowerCase(Locale.ROOT);
        if (woodType.isEmpty()) throw new IllegalArgumentException("wood type is blank");
        if (!Double.isFinite(oxidation) || oxidation < 0 || oxidation > 100
                || !Double.isFinite(storageTemperatureCelsius)
                || !Double.isFinite(caskCleanliness) || caskCleanliness < 0 || caskCleanliness > 100) {
            throw new IllegalArgumentException("invalid aging settings");
        }
    }

    public AgingProfile profile(long durationSeconds) {
        if (durationSeconds < 0) throw new IllegalArgumentException("duration must be non-negative");
        return new AgingProfile(woodType, durationSeconds, oxidation, storageTemperatureCelsius, caskCleanliness);
    }
}
