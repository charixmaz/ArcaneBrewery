package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Map;
import java.util.Set;
import static me.arcanebrewery.arcaneBrewery.application.alchemy.ProfileEffect.*;

/** Generation 2 balance. A catalyst selects the family; the weighted base supports its execution. */
public final class IngredientAffinities {
    private IngredientAffinities() { }

    public record Influence(double suitability, double duration, double stability, double drawback) { }
    private record Base(double fallback, double duration, double stability, double drawback,
                        Map<ProfileEffect, Double> affinities, String hint) {
        double suitability(ProfileEffect effect) { return affinities.getOrDefault(effect, fallback); }
    }
    private static final Base UNKNOWN = new Base(.3, .9, 0, 1, Map.of(),
            "Unresearched base; limited support for advanced effects.");
    private static final Map<String, Base> BASES = Map.of(
        "minecraft:wheat", new Base(.55, 1, 8, 1, Map.of(),
            "Reliable starter base; modest support across effect families."),
        "minecraft:potato", new Base(.25, .75, -4, 1.1,
            Map.of(HASTE, 1.0, STRENGTH, .75, RESISTANCE, .65),
            "Supports mining strength; its effects are short-lived."),
        "minecraft:sugar_cane", new Base(.35, 1.5, -8, 1.2,
            Map.of(SPEED, 1.0, JUMP_BOOST, .85, HASTE, .65, WATER_BREATHING, .65,
                DOLPHINS_GRACE, .65, REGENERATION, .25, ABSORPTION, .25),
            "Extends duration and suits travel; sacrifices stability and mining strength."),
        "minecraft:apple", new Base(.3, 1.05, 8, .8,
            Map.of(REGENERATION, 1.0, ABSORPTION, .75),
            "Supports recovery; poorly suited to mining and combat strength."),
        "minecraft:golden_apple", new Base(.35, .95, 4, .85,
            Map.of(ABSORPTION, 1.0, REGENERATION, .9, RESISTANCE, .85, STRENGTH, .7),
            "Supports protection and recovery rather than long-lasting mining effects."),
        "minecraft:honeycomb", new Base(.4, 1.1, 18, .4,
            Map.of(REGENERATION, .9, ABSORPTION, .8),
            "Stabilizes mixtures and shortens mild drawbacks; dilutes unsupported strength."),
        "minecraft:sweet_berries", new Base(.35, 1.15, -4, 1,
            Map.of(NIGHT_VISION, 1.0, INVISIBILITY, .85, SPEED, .75),
            "Supports vision and concealment; less stable than gentle recovery bases."),
        "arcanebrewery:premium_grain", new Base(.55, .95, 12, .8,
            Map.of(HASTE, .9, RESISTANCE, .85, STRENGTH, .8, FIRE_RESISTANCE, .75),
            "Resilient specialist grain; supports mining and protection, not maximum duration.")
    );

    public static Set<String> supportedIngredients() { return BASES.keySet(); }
    public static String hint(String ingredient) { return BASES.getOrDefault(ingredient, UNKNOWN).hint(); }

    public static Influence blend(Map<String, Double> ratios, ProfileEffect effect) {
        if (ratios.isEmpty() || effect == null) throw new IllegalArgumentException("Missing base or effect");
        double suitability = 0, duration = 0, stability = 0, drawback = 0, total = 0;
        // Sorted iteration makes reversed slot/map order bit-identical.
        for (var entry : new java.util.TreeMap<>(ratios).entrySet()) {
            double weight = entry.getValue();
            if (!Double.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid base ratio");
            var base = BASES.getOrDefault(entry.getKey(), UNKNOWN);
            suitability += weight * base.suitability(effect);
            duration += weight * base.duration();
            stability += weight * base.stability();
            drawback += weight * base.drawback();
            total += weight;
        }
        if (!Double.isFinite(total) || Math.abs(total - 1) > 1e-12)
            throw new IllegalArgumentException("Base ratios must sum to one");
        return new Influence(suitability, duration, stability, drawback);
    }
}
