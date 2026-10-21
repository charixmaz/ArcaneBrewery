package me.arcanebrewery.arcaneBrewery.domain.process;

/** Heads/tails are discarded percentages, hearts retained. Zero passes skips distillation. */
public record DistillationProfile(
        double headsCutPercent,
        double heartsCutPercent,
        double tailsCutPercent,
        double targetTemperatureCelsius,
        int passes
) {
    public DistillationProfile {
        headsCutPercent = DomainValues.nonNegative(headsCutPercent);
        heartsCutPercent = DomainValues.nonNegative(heartsCutPercent);
        tailsCutPercent = DomainValues.nonNegative(tailsCutPercent);
        targetTemperatureCelsius = DomainValues.finite(targetTemperatureCelsius);
        if (passes < 0) throw new IllegalArgumentException("passes must be non-negative");
        double total = headsCutPercent + heartsCutPercent + tailsCutPercent;
        if (total > 100.0001) throw new IllegalArgumentException("distillation cuts cannot exceed 100%");
    }
}
