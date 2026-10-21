package me.arcanebrewery.arcaneBrewery.domain.ingredient;

import java.util.Objects;

public record IngredientProfile(
        String id,
        double sugar,
        double starch,
        double acidity,
        double bitterness,
        double aroma,
        double toxicity,
        double magicalAffinity
) {
    public IngredientProfile {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        sugar = clamp(sugar);
        starch = clamp(starch);
        acidity = clamp(acidity);
        bitterness = clamp(bitterness);
        aroma = clamp(aroma);
        toxicity = clamp(toxicity);
        magicalAffinity = clamp(magicalAffinity);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
