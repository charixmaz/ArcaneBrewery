package me.arcanebrewery.arcaneBrewery.application.consumption;

public record RecoveryCondition(
        IntoxicationState state,
        double hangover,
        double withdrawal,
        long updatedAtEpochMillis
) {
    public RecoveryCondition {
        java.util.Objects.requireNonNull(state);
        if (!Double.isFinite(hangover) || hangover < 0 || hangover > 100)
            throw new IllegalArgumentException("hangover must be within 0..100");
        if (!Double.isFinite(withdrawal) || withdrawal < 0 || withdrawal > 100)
            throw new IllegalArgumentException("withdrawal must be within 0..100");
        if (updatedAtEpochMillis < 0) throw new IllegalArgumentException("invalid recovery timestamp");
    }

    public static RecoveryCondition sober() {
        return new RecoveryCondition(IntoxicationState.SOBER, 0, 0, 0);
    }
}
