package me.arcanebrewery.arcaneBrewery.application.consumption;

/** Fantasy policy is deliberately separate from the opt-in legacy progression. */
public final class PlayerConditionPolicy {
    private final boolean legacy;
    private final RecoveryLifecycle historical = new RecoveryLifecycle();
    public PlayerConditionPolicy(boolean legacy) { this.legacy = legacy; }
    public boolean legacyEnabled() { return legacy; }

    public RecoveryCondition recover(RecoveryCondition current, long now) {
        if (legacy) return historical.recover(current, now);
        if (now < 0) throw new IllegalArgumentException("invalid recovery time");
        double minutes = current.updatedAtEpochMillis() == 0 ? 0
                : Math.max(0, now - current.updatedAtEpochMillis()) / 60_000.0;
        var state = new IntoxicationState(clamp(current.state().intoxication() - minutes * 4),
                0, clamp(current.state().poisoning() - minutes * 2), 0, now);
        return new RecoveryCondition(state, clamp(current.hangover() - minutes * 2), 0, now);
    }
    public RecoveryCondition consume(RecoveryCondition current, DrinkConsumptionProfile elixir, long now) {
        if (legacy) return historical.afterDrink(current, elixir, now);
        var recovered = recover(current, now);
        double saturation = clamp(recovered.state().intoxication()
                + elixir.strength() * 0.42 * (0.85 + elixir.quality() * 0.003)
                + elixir.magicalPotency() * 0.10);
        double contamination = clamp(recovered.state().poisoning() + elixir.contamination() * 0.45);
        double recovery = saturation >= 70 ? Math.max(recovered.hangover(), (saturation - 50) * 1.2) : recovered.hangover();
        return new RecoveryCondition(new IntoxicationState(saturation,0,contamination,0,now),clamp(recovery),0,now);
    }
    public String displayStatus(RecoveryCondition condition) {
        if (legacy) return readable(historical.status(condition).name());
        if (condition.state().poisoning() >= 35) return "Contamination Hazard";
        if (condition.state().intoxication() >= 70) return "Magical Overload";
        if (condition.hangover() >= 25) return "Arcane Recovery";
        if (condition.state().intoxication() >= 12) return "Arcane Saturation";
        return "Clear";
    }
    public static RecoveryCondition importLegacy(RecoveryCondition old) {
        // Import temporary exposure only. Do not revive dependence, withdrawal,
        // tolerance or hangover, and do not erase the original persisted fields.
        return new RecoveryCondition(new IntoxicationState(old.state().intoxication(),0,
                old.state().poisoning(),0,old.updatedAtEpochMillis()),0,0,old.updatedAtEpochMillis());
    }
    public static String readable(String value) {
        return java.util.Arrays.stream(value.toLowerCase(java.util.Locale.ROOT).split("[_: ]+"))
                .filter(part -> !part.isEmpty()).map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
    private static double clamp(double value) { return Math.max(0,Math.min(100,value)); }
}
