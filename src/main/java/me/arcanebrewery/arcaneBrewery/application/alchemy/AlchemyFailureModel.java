package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Objects;

public final class AlchemyFailureModel {
    public AlchemyFailureAssessment assess(
            AlchemyPreparation preparation, AlchemyMachineCondition condition) {
        Objects.requireNonNull(preparation);
        Objects.requireNonNull(condition);

        double load = preparation.additives().values().stream().mapToDouble(Double::doubleValue).sum();
        long diversity = preparation.additives().size();

        double instability = clamp(
                load * 18.0
                + Math.max(0, diversity - 1) * 16.0
                + (100.0 - condition.integrity()) * 0.45
                + condition.contamination() * 0.35
        );

        if (instability < 55) {
            return new AlchemyFailureAssessment(instability,
                    AlchemyFailureOutcome.STABLE_SUCCESS,
                    1.5 + instability * 0.02,
                    instability * 0.01);
        }
        if (instability < 75) {
            return new AlchemyFailureAssessment(instability,
                    AlchemyFailureOutcome.UNSTABLE_SUCCESS,
                    4.0 + (instability - 55) * 0.12,
                    2.0 + (instability - 55) * 0.08);
        }
        if (instability < 90) {
            return new AlchemyFailureAssessment(instability,
                    AlchemyFailureOutcome.FAILED_INFUSION,
                    9.0 + (instability - 75) * 0.25,
                    7.0 + (instability - 75) * 0.20);
        }
        return new AlchemyFailureAssessment(instability,
                AlchemyFailureOutcome.CRITICAL_FAILURE,
                Math.min(30.0, 18.0 + (instability - 90) * 0.8),
                Math.min(25.0, 14.0 + (instability - 90) * 0.6));
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
