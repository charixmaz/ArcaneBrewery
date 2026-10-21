package me.arcanebrewery.arcaneBrewery.application.alchemy;

public record AlchemyBalanceProfile(
        long processingDurationMillis,
        double unstableThreshold,
        double failedThreshold,
        double criticalThreshold,
        double loadWeight,
        double diversityWeight,
        double integrityWeight,
        double contaminationWeight,
        double repairIntegrity,
        double cleaningAmount,
        float criticalExplosionPower
) {
    public static final AlchemyBalanceProfile DEFAULT = new AlchemyBalanceProfile(
            8_000L, 55.0, 75.0, 90.0,
            18.0, 16.0, 0.45, 0.35,
            20.0, 25.0, 1.5f
    );

    public AlchemyBalanceProfile {
        if (processingDurationMillis <= 0)
            throw new IllegalArgumentException("processing duration must be positive");
        percent(unstableThreshold, "unstable threshold");
        percent(failedThreshold, "failed threshold");
        percent(criticalThreshold, "critical threshold");
        if (!(unstableThreshold < failedThreshold && failedThreshold < criticalThreshold))
            throw new IllegalArgumentException("failure thresholds must be strictly increasing");
        nonNegative(loadWeight, "load weight");
        nonNegative(diversityWeight, "diversity weight");
        nonNegative(integrityWeight, "integrity weight");
        nonNegative(contaminationWeight, "contamination weight");
        percent(repairIntegrity, "repair integrity");
        percent(cleaningAmount, "cleaning amount");
        if (!Float.isFinite(criticalExplosionPower) || criticalExplosionPower < 0 || criticalExplosionPower > 4)
            throw new IllegalArgumentException("critical explosion power must be within 0..4");
    }

    private static void percent(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 100)
            throw new IllegalArgumentException(name + " must be within 0..100");
    }

    private static void nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException(name + " must be non-negative");
    }
}
