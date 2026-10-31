package me.arcanebrewery.arcaneBrewery.application.consumption;

public record RecoveryCondition(ManaState state, double depletion, long updatedAtEpochMillis) {
    public RecoveryCondition {
        java.util.Objects.requireNonNull(state);
        if (!Double.isFinite(depletion) || depletion < 0 || depletion > 100)
            throw new IllegalArgumentException("mana depletion must be within 0..100");
        if (updatedAtEpochMillis < 0) throw new IllegalArgumentException("invalid recovery timestamp");
    }
    public static RecoveryCondition clear() { return new RecoveryCondition(ManaState.CLEAR,0,0); }
}
