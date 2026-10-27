package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Objects;

public final class AlchemyFailureModel {
    private final AlchemyBalanceProfile balance;

    public AlchemyFailureModel() {
        this(AlchemyBalanceProfile.DEFAULT);
    }

    public AlchemyFailureModel(AlchemyBalanceProfile balance) {
        this.balance = Objects.requireNonNull(balance);
    }

    public AlchemyFailureAssessment assess(
            AlchemyPreparation preparation, AlchemyMachineCondition condition) {
        Objects.requireNonNull(preparation);
        Objects.requireNonNull(condition);

        double load=preparation.additives().entrySet().stream().mapToDouble(e->e.getValue()*ReagentRegistry.additive(e.getKey()).map(d->switch(d.role()) {
            case EFFECT_CATALYST -> 1.0;case DURATION_REAGENT,POTENCY_REAGENT -> .25;case STABILIZER,FUTURE_PURIFICATION -> 0.0;
        }).orElse(1.0)).sum();
        long diversity=preparation.additives().keySet().stream().filter(id->ReagentRegistry.additive(id).map(d->d.role()==ReagentRole.EFFECT_CATALYST).orElse(true)).count();

        double instability = clamp(
                load * balance.loadWeight()
                + Math.max(0, diversity - 1) * balance.diversityWeight()
                + (100.0 - condition.integrity()) * balance.integrityWeight()
                + condition.contamination() * balance.contaminationWeight()
        );

        double stabilizers=preparation.additives().entrySet().stream().mapToDouble(e->ReagentRegistry.additive(e.getKey()).map(d->e.getValue()*d.stabilization()).orElse(0.0)).sum();
        instability=clamp(instability-stabilizers);
        if (instability < balance.unstableThreshold()) {
            return new AlchemyFailureAssessment(instability,
                    AlchemyFailureOutcome.STABLE_SUCCESS,
                    1.5 + instability * 0.02,
                    instability * 0.01);
        }
        if (instability < balance.failedThreshold()) {
            return new AlchemyFailureAssessment(instability,
                    AlchemyFailureOutcome.UNSTABLE_SUCCESS,
                    4.0 + (instability - balance.unstableThreshold()) * 0.12,
                    2.0 + (instability - balance.unstableThreshold()) * 0.08);
        }
        if (instability < balance.criticalThreshold()) {
            return new AlchemyFailureAssessment(instability,
                    AlchemyFailureOutcome.FAILED_INFUSION,
                    9.0 + (instability - balance.failedThreshold()) * 0.25,
                    7.0 + (instability - balance.failedThreshold()) * 0.20);
        }
        return new AlchemyFailureAssessment(instability,
                AlchemyFailureOutcome.CRITICAL_FAILURE,
                Math.min(30.0, 18.0 + (instability - balance.criticalThreshold()) * 0.8),
                Math.min(25.0, 14.0 + (instability - balance.criticalThreshold()) * 0.6));
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
