package me.arcanebrewery.arcaneBrewery.application.consumption;

public final class IntoxicationModel {
    private static final double INTOX_DECAY_PER_MINUTE = 4.0;
    private static final double POISON_DECAY_PER_MINUTE = 2.0;
    private static final double TOLERANCE_DECAY_PER_HOUR = 1.0;
    private static final double DEPENDENCE_DECAY_PER_HOUR = 0.35;

    public IntoxicationState recover(IntoxicationState state, long nowEpochMillis) {
        if (state.updatedAtEpochMillis() == 0) return new IntoxicationState(
                state.intoxication(), state.tolerance(), state.poisoning(), state.dependence(), nowEpochMillis);
        long elapsed = Math.max(0, nowEpochMillis - state.updatedAtEpochMillis());
        double minutes = elapsed / 60_000.0;
        double hours = elapsed / 3_600_000.0;
        return new IntoxicationState(
                clamp(state.intoxication() - INTOX_DECAY_PER_MINUTE * minutes),
                clamp(state.tolerance() - TOLERANCE_DECAY_PER_HOUR * hours),
                clamp(state.poisoning() - POISON_DECAY_PER_MINUTE * minutes),
                clamp(state.dependence() - DEPENDENCE_DECAY_PER_HOUR * hours),
                nowEpochMillis
        );
    }

    public ConsumptionOutcome consume(
            IntoxicationState current, DrinkConsumptionProfile drink, long nowEpochMillis) {
        IntoxicationState recovered = recover(current, nowEpochMillis);

        double toleranceFactor = 1.0 - recovered.tolerance() * 0.0045;
        double qualityFactor = 0.85 + drink.quality() * 0.003;
        double dose = drink.strength() * 0.42 * toleranceFactor * qualityFactor;

        double contaminationPoison = drink.contamination() * 0.45;
        double strengthPoison = Math.max(0, drink.strength() - 75) * 0.25;
        double poisoning = clamp(recovered.poisoning() + contaminationPoison + strengthPoison);

        double intoxication = clamp(recovered.intoxication() + dose);
        double tolerance = clamp(recovered.tolerance() + drink.strength() * 0.018);
        double dependenceGain = intoxication >= 45 ? drink.strength() * 0.003 : 0;
        double dependence = clamp(recovered.dependence() + dependenceGain);

        IntoxicationState next = new IntoxicationState(
                intoxication, tolerance, poisoning, dependence, nowEpochMillis);

        ConsumptionOutcome.Severity severity = severity(intoxication, poisoning);
        boolean nausea = intoxication >= 55 || poisoning >= 25 || drink.contamination() >= 45;
        boolean hangover = intoxication >= 40 || poisoning >= 20;
        boolean poisonRisk = poisoning >= 35;

        return new ConsumptionOutcome(next, severity, nausea, hangover, poisonRisk);
    }

    public boolean withdrawalRisk(IntoxicationState state, long nowEpochMillis) {
        IntoxicationState recovered = recover(state, nowEpochMillis);
        return recovered.dependence() >= 35 && recovered.intoxication() < 10;
    }

    private ConsumptionOutcome.Severity severity(double intoxication, double poisoning) {
        if (poisoning >= 65 || intoxication >= 90) return ConsumptionOutcome.Severity.DANGEROUS;
        if (intoxication >= 65) return ConsumptionOutcome.Severity.HEAVILY_DRUNK;
        if (intoxication >= 35) return ConsumptionOutcome.Severity.DRUNK;
        if (intoxication >= 12) return ConsumptionOutcome.Severity.BUZZED;
        return ConsumptionOutcome.Severity.SOBER;
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
