package me.arcanebrewery.arcaneBrewery.application.consumption;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;

public final class DrinkStrengthCalculator {
    public double calculate(ProcessProfile process) {
        java.util.Objects.requireNonNull(process);
        var distillation = process.distillation();
        double fermentationBase = Math.min(18.0,
                4.0 + process.fermentation().durationSeconds() / 60.0 * 1.5);
        if (distillation.passes() <= 0) return clamp(fermentationBase);

        double heartsFactor = distillation.heartsCutPercent() / 80.0;
        double temperatureFactor = 1.0 - Math.min(0.25,
                Math.abs(distillation.targetTemperatureCelsius() - 78.0) / 100.0);
        double distilled = fermentationBase + 18.0 * distillation.passes() * heartsFactor * temperatureFactor;
        return clamp(distilled);
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
