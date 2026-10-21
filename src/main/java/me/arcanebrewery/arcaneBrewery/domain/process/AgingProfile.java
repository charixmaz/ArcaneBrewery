package me.arcanebrewery.arcaneBrewery.domain.process;

public record AgingProfile(
        String woodType,
        long durationSeconds,
        double oxidation,
        double storageTemperatureCelsius,
        double caskCleanliness
) {
    public AgingProfile {
        woodType = DomainValues.id(woodType);
        storageTemperatureCelsius = DomainValues.finite(storageTemperatureCelsius);
        if (durationSeconds < 0) throw new IllegalArgumentException("durationSeconds must be non-negative");
        oxidation = clamp(oxidation);
        caskCleanliness = clamp(caskCleanliness);
    }

    private static double clamp(double value) {
        return DomainValues.percent(value);
    }
}
