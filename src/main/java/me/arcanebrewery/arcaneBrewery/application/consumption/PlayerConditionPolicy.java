package me.arcanebrewery.arcaneBrewery.application.consumption;

/** Deterministic mana exposure and recovery; no alternate consumption mode. */
public final class PlayerConditionPolicy {

    public RecoveryCondition recover(RecoveryCondition current, long now) {
        if (now < 0) throw new IllegalArgumentException("invalid recovery time");
        double minutes = current.updatedAtEpochMillis() == 0 ? 0
                : Math.max(0, now - current.updatedAtEpochMillis()) / 60_000.0;
        var state = new ManaState(clamp(current.state().load() - minutes * 4), clamp(current.state().hazard() - minutes * 2), now);
        return new RecoveryCondition(state, clamp(current.depletion() - minutes * 2), now);
    }
    public RecoveryCondition consume(RecoveryCondition current, DrinkConsumptionProfile elixir, long now) {
        var recovered = recover(current, now);
        double saturation = clamp(recovered.state().load()
                + elixir.strength() * 0.42 * (0.85 + elixir.quality() * 0.003)
                + elixir.magicalPotency() * 0.10);
        double contamination = 0;
        double recovery = saturation >= 70 ? Math.max(recovered.depletion(), (saturation - 50) * 1.2) : recovered.depletion();
        return new RecoveryCondition(new ManaState(saturation,contamination,now),clamp(recovery),now);
    }
    public String displayStatus(RecoveryCondition condition) {

        if (condition.state().load() >= 70) return "Mana Overload";
        if (condition.depletion() >= 25) return "Mana Depletion";
        if (condition.state().load() >= 12) return "Mana Charged";
        return "Clear";
    }
    public static String readable(String value) {
        return java.util.Arrays.stream(value.toLowerCase(java.util.Locale.ROOT).split("[_: ]+"))
                .filter(part -> !part.isEmpty()).map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
    private static double clamp(double value) { return Math.max(0,Math.min(100,value)); }
}
