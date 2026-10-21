package me.arcanebrewery.arcaneBrewery.application.consumption;

public final class RecoveryLifecycle {
    private static final double HANGOVER_DECAY_PER_MINUTE = 1.5;
    private static final double WITHDRAWAL_DECAY_PER_MINUTE = 0.5;

    private final IntoxicationModel intoxication = new IntoxicationModel();

    public RecoveryCondition afterDrink(
            RecoveryCondition current, DrinkConsumptionProfile drink, long nowEpochMillis) {
        RecoveryCondition recovered = recover(current, nowEpochMillis);
        ConsumptionOutcome outcome = intoxication.consume(recovered.state(), drink, nowEpochMillis);

        double hangover = recovered.hangover();
        if (outcome.hangoverRisk()) {
            hangover = clamp(Math.max(hangover,
                    outcome.state().intoxication() * 0.45 + outcome.state().poisoning() * 0.55));
        }

        double withdrawal = recovered.withdrawal();
        if (outcome.state().dependence() >= 35) {
            withdrawal = Math.max(0, withdrawal - drink.strength() * 0.20);
        }

        return new RecoveryCondition(outcome.state(), hangover, clamp(withdrawal), nowEpochMillis);
    }

    public RecoveryCondition recover(RecoveryCondition current, long nowEpochMillis) {
        if (current.updatedAtEpochMillis() == 0) {
            IntoxicationState state = intoxication.recover(current.state(), nowEpochMillis);
            return new RecoveryCondition(state, current.hangover(), current.withdrawal(), nowEpochMillis);
        }

        long elapsed = Math.max(0, nowEpochMillis - current.updatedAtEpochMillis());
        double minutes = elapsed / 60_000.0;
        IntoxicationState state = intoxication.recover(current.state(), nowEpochMillis);

        double hangover = clamp(current.hangover() - HANGOVER_DECAY_PER_MINUTE * minutes);
        double withdrawal = clamp(current.withdrawal() - WITHDRAWAL_DECAY_PER_MINUTE * minutes);

        if (state.dependence() >= 35 && state.intoxication() < 10) {
            double dependencePressure = (state.dependence() - 35) * 0.08 * minutes;
            withdrawal = clamp(Math.max(withdrawal, dependencePressure));
        }

        return new RecoveryCondition(state, hangover, withdrawal, nowEpochMillis);
    }

    public Status status(RecoveryCondition condition) {
        if (condition.withdrawal() >= 60) return Status.SEVERE_WITHDRAWAL;
        if (condition.hangover() >= 60) return Status.SEVERE_HANGOVER;
        if (condition.withdrawal() >= 25) return Status.WITHDRAWAL;
        if (condition.hangover() >= 25) return Status.HANGOVER;
        if (condition.state().intoxication() >= 35) return Status.DRUNK;
        if (condition.state().intoxication() >= 12) return Status.BUZZED;
        return Status.CLEAR;
    }

    public enum Status {
        CLEAR, BUZZED, DRUNK, HANGOVER, SEVERE_HANGOVER, WITHDRAWAL, SEVERE_WITHDRAWAL
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
