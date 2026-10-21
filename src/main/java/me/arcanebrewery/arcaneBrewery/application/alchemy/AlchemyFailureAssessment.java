package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Objects;

public record AlchemyFailureAssessment(
        double instability,
        AlchemyFailureOutcome outcome,
        double integrityDamage,
        double contaminationGain
) {
    public AlchemyFailureAssessment {
        if (!Double.isFinite(instability) || instability < 0 || instability > 100)
            throw new IllegalArgumentException("instability must be within 0..100");
        Objects.requireNonNull(outcome);
        if (!Double.isFinite(integrityDamage) || integrityDamage < 0 || integrityDamage > 100)
            throw new IllegalArgumentException("integrity damage must be within 0..100");
        if (!Double.isFinite(contaminationGain) || contaminationGain < 0 || contaminationGain > 100)
            throw new IllegalArgumentException("contamination gain must be within 0..100");
    }

    public boolean producesPreparedBase() {
        return outcome == AlchemyFailureOutcome.STABLE_SUCCESS
                || outcome == AlchemyFailureOutcome.UNSTABLE_SUCCESS;
    }
}
