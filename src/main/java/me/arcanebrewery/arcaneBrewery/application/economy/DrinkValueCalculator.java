package me.arcanebrewery.arcaneBrewery.application.economy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyEffect;
import me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyResult;
import me.arcanebrewery.arcaneBrewery.application.consumption.DrinkStrengthCalculator;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import me.arcanebrewery.arcaneBrewery.domain.quality.BatchQuality;

import java.util.Objects;

public final class DrinkValueCalculator {
    private final DrinkStrengthCalculator strengthCalculator = new DrinkStrengthCalculator();

    public DrinkValue calculate(ProcessProfile process, BatchQuality quality, AlchemyResult alchemy) {
        Objects.requireNonNull(process);
        Objects.requireNonNull(quality);
        Objects.requireNonNull(alchemy);

        double qualityComponent = quality.overallScore() * 0.90
                + quality.purity() * 0.20
                + quality.flavor() * 0.15
                + quality.aroma() * 0.10;

        double strength = strengthCalculator.calculate(process);
        double agingDays = process.aging().durationSeconds() / 86_400.0;
        double agingValue = Math.log1p(Math.max(0, agingDays)) * 18.0;
        double processComponent = strength * 0.30
                + process.distillation().passes() * 7.5
                + agingValue
                + process.alchemicalAdditives().values().stream().mapToDouble(Double::doubleValue).sum() * 5.0;

        double effectPremium = switch (alchemy.effect()) {
            case NONE -> 0.0;
            case FIRE_RESISTANCE, NIGHT_VISION -> 8.0;
            case REGENERATION, INVISIBILITY, LEVITATION -> 14.0;
            case TELEPORTATION, HALLUCINATION -> 20.0;
            case CURSE -> 10.0;
            case WITHER -> 16.0;
        };
        double alchemyComponent = effectPremium + alchemy.potency() * 0.28
                + (alchemy.dangerousReaction() ? 6.0 : 0.0);

        double contaminationPenalty = quality.contamination() * 0.85;
        double units = clamp(10.0 + qualityComponent + processComponent + alchemyComponent - contaminationPenalty);

        return new DrinkValue(
                DrinkValue.VERSION,
                round2(units),
                round2(qualityComponent),
                round2(processComponent),
                round2(alchemyComponent),
                round2(contaminationPenalty)
        );
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(10_000, value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
