package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.util.Objects;

/** Pure calculation: identical process, conditions, targets and variation give identical results. */
public final class BatchQualityCalculator {
    public BatchSimulationResult calculate(ProcessProfile process, QualityTargets targets,
            BatchConditions conditions, BatchVariation variation) {
        Objects.requireNonNull(process);
        Objects.requireNonNull(targets);
        Objects.requireNonNull(conditions);
        Objects.requireNonNull(variation);
        if (!conditions.ingredients().keySet().equals(process.ingredientRatios().keySet()))
            throw new IllegalArgumentException("ingredient lots must exactly match the recipe");
        double purity = 0, fermentables = 0, aroma = 0, bitterness = 0;
        double acidity = 0, toxicity = 0, affinity = 0;
        for (var entry : process.ingredientRatios().entrySet()) {
            var lot = conditions.ingredients().get(entry.getKey());
            var composition = lot.composition();
            double ratio = entry.getValue();
            purity += ratio * lot.purity();
            fermentables += ratio * (composition.sugar() + composition.starch()) / 2;
            aroma += ratio * composition.aroma();
            bitterness += ratio * composition.bitterness();
            acidity += ratio * composition.acidity();
            toxicity += ratio * composition.toxicity();
            affinity += ratio * composition.magicalAffinity();
        }
        var fermentation = process.fermentation();
        double temperatureAccuracy = Math.max(0, 100 - 100 * Math.abs(
                fermentation.temperatureCelsius() - targets.fermentationTemperature()) / targets.temperatureTolerance());
        double durationAccuracy = Math.max(0, 100 - 100 * Math.abs(
                (double) fermentation.durationSeconds() / targets.fermentationSeconds() - 1));
        var contamination = new ContaminationModel().evaluate(process, conditions, toxicity, temperatureAccuracy);
        var distillation = process.distillation();
        double cutPenalty = distillation.passes() == 0 ? 0
                : Math.max(0, 10 - distillation.headsCutPercent()) * 2
                + Math.max(0, 10 - distillation.tailsCutPercent())
                + Math.abs(distillation.heartsCutPercent() - 80) * 0.15;
        // Diminishing returns for purification, with flavor loss on repeated passes.
        double passBenefit = 6 * Math.min(3, distillation.passes());
        double passFlavorLoss = 3.0 * Math.max(0, distillation.passes() - 1);
        var aging = process.aging();
        double maturation = targets.agingSeconds() == 0 ? 0
                : (double) aging.durationSeconds() / targets.agingSeconds();
        double agingBenefit = 12 * Math.min(1, maturation) * conditions.wood().suitability() / 100;
        double overAging = targets.agingSeconds() == 0
                ? (aging.durationSeconds() > 0 ? 15 : 0) : Math.min(100, Math.max(0, maturation - 1) * 20);
        double storagePenalty = aging.durationSeconds() == 0 ? 0
                : Math.min(50, Math.abs(aging.storageTemperatureCelsius() - targets.storageTemperature()) * 0.5)
                + aging.oxidation() * 0.2 + (100 - aging.caskCleanliness()) * 0.1;
        double penalty = contamination.burden() * 0.35 + contamination.toxicity() * 0.4;
        double flavor = 25 + temperatureAccuracy * 0.18 + durationAccuracy * 0.15
                + fermentables * 0.1 + aroma * 0.1 - Math.abs(acidity - 30) * 0.05
                - bitterness * 0.04 + agingBenefit * conditions.wood().flavorContribution() / 100
                - overAging - storagePenalty - cutPenalty - passFlavorLoss - penalty;
        var quality = new BatchQuality(
                purity * 0.8 + temperatureAccuracy * 0.1 + durationAccuracy * 0.1
                        + passBenefit - cutPenalty - penalty + variation.quality(),
                flavor + variation.flavor(),
                aroma * 0.6 + temperatureAccuracy * 0.2 + durationAccuracy * 0.2
                        + agingBenefit / 2 - storagePenalty - overAging / 2 - penalty + variation.quality(),
                fermentation.sanitation() * 0.5 + conditions.catalystStability() * 0.3
                        + durationAccuracy * 0.2 + (aging.durationSeconds() > 0
                        ? conditions.wood().preservation() * 0.05 : 0)
                        - penalty - storagePenalty + variation.quality(),
                Math.max(contamination.burden(), contamination.toxicity()),
                affinity * conditions.catalystStability() / 100 * (0.5 + temperatureAccuracy / 200)
                        - penalty - overAging / 2 + variation.potency());
        return new BatchSimulationResult(DrinkFingerprint.from(process), quality, contamination);
    }
}
