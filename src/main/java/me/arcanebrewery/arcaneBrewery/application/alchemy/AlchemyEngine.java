package me.arcanebrewery.arcaneBrewery.application.alchemy;

import me.arcanebrewery.arcaneBrewery.application.ingredient.IngredientRegistry;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import me.arcanebrewery.arcaneBrewery.domain.quality.BatchQuality;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class AlchemyEngine {
    private final IngredientRegistry ingredients;

    public AlchemyEngine(IngredientRegistry ingredients) {
        this.ingredients = Objects.requireNonNull(ingredients);
    }

    public AlchemyResult evaluate(ProcessProfile process, BatchQuality quality) {
        Objects.requireNonNull(process);
        Objects.requireNonNull(quality);

        double weightedAffinity = process.ingredientRatios().entrySet().stream()
                .mapToDouble(e -> e.getValue() * ingredients.require(e.getKey()).profile().magicalAffinity())
                .sum();
        double weightedToxicity = process.ingredientRatios().entrySet().stream()
                .mapToDouble(e -> e.getValue() * ingredients.require(e.getKey()).profile().toxicity())
                .sum();

        String dominant = process.ingredientRatios().entrySet().stream()
                .max(Map.Entry.<String, Double>comparingByValue()
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .orElseThrow().getKey();

        AlchemyEffect effect = effectFor(process, dominant, weightedAffinity);
        double additiveLoad = process.alchemicalAdditives().values().stream().mapToDouble(Double::doubleValue).sum();

        double potency = clamp(weightedAffinity * 0.55
                + quality.magicalPotency() * 0.45
                + Math.min(20.0, additiveLoad * 5.0));

        double instability = clamp(
                weightedToxicity * 0.45
                + quality.contamination() * 0.35
                + Math.max(0, weightedAffinity - quality.stability()) * 0.35
                + additiveLoad * 2.5);

        boolean dangerous = instability >= 70
                || effect == AlchemyEffect.WITHER
                || effect == AlchemyEffect.CURSE && instability >= 45;

        if (weightedAffinity < 20 && process.alchemicalAdditives().isEmpty()) {
            effect = AlchemyEffect.NONE;
            potency = 0;
        }

        return new AlchemyResult(effect, clamp(weightedAffinity), potency, instability, dangerous);
    }

    private AlchemyEffect effectFor(ProcessProfile process, String dominant, double affinity) {
        var selected=process.alchemicalAdditives().keySet().stream().map(ReagentRegistry::additive).flatMap(java.util.Optional::stream)
                .filter(d->!d.effects().isEmpty()).max(java.util.Comparator.comparingInt(ReagentDefinition::priority));
        if(selected.isPresent()) {
            try {return AlchemyEffect.valueOf(selected.get().effects().getFirst().name());}
            catch(IllegalArgumentException newFamily) {return AlchemyEffect.NONE;}
        }

        return switch (dominant) {
            case "minecraft:blaze_powder" -> AlchemyEffect.FIRE_RESISTANCE;
            case "minecraft:chorus_fruit" -> AlchemyEffect.TELEPORTATION;
            case "minecraft:glistering_melon_slice" -> AlchemyEffect.REGENERATION;
            case "minecraft:glowstone_dust" -> AlchemyEffect.NIGHT_VISION;
            case "minecraft:amethyst_shard" -> AlchemyEffect.INVISIBILITY;
            case "minecraft:shulker_shell" -> AlchemyEffect.LEVITATION;
            case "minecraft:echo_shard" -> affinity >= 80 ? AlchemyEffect.HALLUCINATION : AlchemyEffect.NIGHT_VISION;
            case "minecraft:wither_skeleton_skull" -> AlchemyEffect.WITHER;
            case "minecraft:netherite_scrap" -> AlchemyEffect.CURSE;
            default -> affinity >= 70 ? AlchemyEffect.HALLUCINATION : AlchemyEffect.NONE;
        };
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
