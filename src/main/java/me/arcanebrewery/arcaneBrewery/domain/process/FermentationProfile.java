package me.arcanebrewery.arcaneBrewery.domain.process;

public record FermentationProfile(
        String yeastOrCatalyst,
        double temperatureCelsius,
        long durationSeconds,
        double sanitation,
        double oxygenExposure
) {
    public FermentationProfile {
        if (durationSeconds < 0) throw new IllegalArgumentException("durationSeconds must be non-negative");
        sanitation = clamp(sanitation);
        oxygenExposure = clamp(oxygenExposure);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
