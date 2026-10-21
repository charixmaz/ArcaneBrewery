package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Objects;

public record AlchemyResult(
        AlchemyEffect effect,
        double affinity,
        double potency,
        double instability,
        boolean dangerousReaction
) {
    public AlchemyResult {
        Objects.requireNonNull(effect);
        affinity = percent(affinity, "affinity");
        potency = percent(potency, "potency");
        instability = percent(instability, "instability");
    }

    private static double percent(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 100)
            throw new IllegalArgumentException(name + " must be within 0..100");
        return value;
    }
}
