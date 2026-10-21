package me.arcanebrewery.arcaneBrewery.application.consumption;

public record ConsumptionOutcome(
        IntoxicationState state,
        Severity severity,
        boolean nausea,
        boolean hangoverRisk,
        boolean poisoningRisk
) {
    public enum Severity { SOBER, BUZZED, DRUNK, HEAVILY_DRUNK, DANGEROUS }

    public ConsumptionOutcome {
        java.util.Objects.requireNonNull(state);
        java.util.Objects.requireNonNull(severity);
    }
}
