package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;

/** Condition-derived burden; these are game balance rules, not a food-safety model. */
public final class ContaminationModel {
    public Contamination evaluate(ProcessProfile process, BatchConditions conditions,
            double ingredientToxicity, double temperatureAccuracy) {
        var fermentation = process.fermentation();
        var aging = process.aging();
        double burden = conditions.contaminationLoad()
                + (100 - fermentation.sanitation()) * 0.45
                + fermentation.oxygenExposure() * 0.15
                + (100 - temperatureAccuracy) * 0.1;
        if (aging.durationSeconds() > 0) {
            burden += (100 - aging.caskCleanliness()) * 0.3 + aging.oxidation() * 0.2;
        }
        var distillation = process.distillation();
        double toxicity = ingredientToxicity;
        if (distillation.passes() > 0) {
            // Inadequate discarded heads/tails retain unwanted compounds.
            toxicity += Math.max(0, 10 - distillation.headsCutPercent()) * 5
                    + Math.max(0, 10 - distillation.tailsCutPercent()) * 2;
        }
        return new Contamination(burden, toxicity);
    }
}
