package me.arcanebrewery.arcaneBrewery.application.consumption;

public record IntoxicationState(
        double intoxication,
        double tolerance,
        double poisoning,
        double dependence,
        long updatedAtEpochMillis
) {
    public static final IntoxicationState SOBER = new IntoxicationState(0, 0, 0, 0, 0);

    public IntoxicationState {
        intoxication = percent(intoxication, "intoxication");
        tolerance = percent(tolerance, "tolerance");
        poisoning = percent(poisoning, "poisoning");
        dependence = percent(dependence, "dependence");
        if (updatedAtEpochMillis < 0) throw new IllegalArgumentException("invalid update timestamp");
    }

    private static double percent(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 100)
            throw new IllegalArgumentException(name + " must be within 0..100");
        return value;
    }
}
