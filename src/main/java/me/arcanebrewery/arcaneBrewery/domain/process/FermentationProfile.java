package me.arcanebrewery.arcaneBrewery.domain.process;

public record FermentationProfile(
        String yeastOrCatalyst,
        double temperatureCelsius,
        long durationSeconds,
        double sanitation,
        double oxygenExposure
) {
    public FermentationProfile {
        yeastOrCatalyst = DomainValues.id(yeastOrCatalyst);
        temperatureCelsius = DomainValues.finite(temperatureCelsius);
        if (durationSeconds < 0) throw new IllegalArgumentException("durationSeconds must be non-negative");
        sanitation = clamp(sanitation);
        oxygenExposure = clamp(oxygenExposure);
    }

    private static double clamp(double value) {
        return DomainValues.percent(value);
    }
}
