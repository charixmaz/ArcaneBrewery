package me.arcanebrewery.arcaneBrewery.application.distillery;

public record DistillationControls(
        double temperatureCelsius,
        double headsCutPercent,
        double heartsCutPercent,
        double tailsCutPercent,
        int passes
) {
    public static final DistillationControls DEFAULT = new DistillationControls(78.0, 10.0, 80.0, 10.0, 1);

    public DistillationControls {
        if (!Double.isFinite(temperatureCelsius)
                || !Double.isFinite(headsCutPercent)
                || !Double.isFinite(heartsCutPercent)
                || !Double.isFinite(tailsCutPercent)) {
            throw new IllegalArgumentException("distillation controls must be finite");
        }
        if (headsCutPercent < 0 || heartsCutPercent < 0 || tailsCutPercent < 0 || passes < 0) {
            throw new IllegalArgumentException("distillation controls must be non-negative");
        }
        if (headsCutPercent + heartsCutPercent + tailsCutPercent > 100.0001) {
            throw new IllegalArgumentException("distillation cuts cannot exceed 100%");
        }
    }
}
