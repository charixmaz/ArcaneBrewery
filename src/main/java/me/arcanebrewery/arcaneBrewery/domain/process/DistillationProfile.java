package me.arcanebrewery.arcaneBrewery.domain.process;

public record DistillationProfile(
        double headsCutPercent,
        double heartsCutPercent,
        double tailsCutPercent,
        double targetTemperatureCelsius,
        int passes
) {
    public DistillationProfile {
        if (passes < 0) throw new IllegalArgumentException("passes must be non-negative");
        if (headsCutPercent < 0 || heartsCutPercent < 0 || tailsCutPercent < 0) {
            throw new IllegalArgumentException("distillation cuts must be non-negative");
        }
        double total = headsCutPercent + heartsCutPercent + tailsCutPercent;
        if (total > 100.0001) throw new IllegalArgumentException("distillation cuts cannot exceed 100%");
    }
}
