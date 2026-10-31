package me.arcanebrewery.arcaneBrewery.application.consumption;

public record ManaState(double load, double hazard, long updatedAtEpochMillis) {
    public static final ManaState CLEAR = new ManaState(0,0,0);
    public ManaState {
        if (!Double.isFinite(load) || load < 0 || load > 100 || !Double.isFinite(hazard) || hazard < 0 || hazard > 100)
            throw new IllegalArgumentException("mana values must be within 0..100");
        if (updatedAtEpochMillis < 0) throw new IllegalArgumentException("invalid update timestamp");
    }
}
