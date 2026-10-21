package me.arcanebrewery.arcaneBrewery.application.economy;

public record DrinkValue(
        int version,
        double units,
        double qualityComponent,
        double processComponent,
        double alchemyComponent,
        double contaminationPenalty
) {
    public static final int VERSION = 1;

    public DrinkValue {
        if (version != VERSION) throw new IllegalArgumentException("unsupported drink value version");
        units = nonNegative(units, "units");
        qualityComponent = nonNegative(qualityComponent, "quality component");
        processComponent = nonNegative(processComponent, "process component");
        alchemyComponent = nonNegative(alchemyComponent, "alchemy component");
        contaminationPenalty = nonNegative(contaminationPenalty, "contamination penalty");
    }

    private static double nonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        return value;
    }
}
