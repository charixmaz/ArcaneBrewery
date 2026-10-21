package me.arcanebrewery.arcaneBrewery.application.consumption;

public record DrinkConsumptionProfile(
        double strength,
        double quality,
        double contamination,
        double magicalPotency
) {
    public DrinkConsumptionProfile {
        strength = percent(strength, "strength");
        quality = percent(quality, "quality");
        contamination = percent(contamination, "contamination");
        magicalPotency = percent(magicalPotency, "magicalPotency");
    }

    private static double percent(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 100)
            throw new IllegalArgumentException(name + " must be within 0..100");
        return value;
    }
}
