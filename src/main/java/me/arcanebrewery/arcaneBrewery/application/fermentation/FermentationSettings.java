package me.arcanebrewery.arcaneBrewery.application.fermentation;

import me.arcanebrewery.arcaneBrewery.domain.process.FermentationProfile;

import java.util.Locale;
import java.util.Objects;

public record FermentationSettings(
        String yeastOrCatalyst,
        double temperatureCelsius,
        long durationSeconds,
        double sanitation,
        double oxygenExposure
) {
    public static final FermentationSettings DEFAULT =
            new FermentationSettings("arcanebrewery:wild_yeast", 22.0, 60L, 85.0, 10.0);

    public FermentationSettings {
        yeastOrCatalyst = Objects.requireNonNull(yeastOrCatalyst, "yeastOrCatalyst")
                .strip().toLowerCase(Locale.ROOT);
        if (yeastOrCatalyst.isEmpty()) throw new IllegalArgumentException("yeast/catalyst is blank");
        if (!Double.isFinite(temperatureCelsius) || durationSeconds <= 0
                || !Double.isFinite(sanitation) || sanitation < 0 || sanitation > 100
                || !Double.isFinite(oxygenExposure) || oxygenExposure < 0 || oxygenExposure > 100) {
            throw new IllegalArgumentException("invalid fermentation settings");
        }
    }

    public FermentationProfile profile() {
        return new FermentationProfile(yeastOrCatalyst, temperatureCelsius, durationSeconds, sanitation, oxygenExposure);
    }
}
