package me.arcanebrewery.arcaneBrewery.application.aging;

import me.arcanebrewery.arcaneBrewery.application.distillery.DistillationControls;
import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentedProcessDraft;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;

import java.util.Map;
import java.util.Objects;

public final class AgingService {
    private final BatchQualityCalculator calculator = new BatchQualityCalculator();

    public AgingResult finish(FermentedProcessDraft source, AgingBatchState aging,
            long nowEpochMillis, Map<String, IngredientLot> lots,
            WoodCharacteristics wood, double contaminationLoad,
            QualityTargets targets, long variationSeed) {
        return finish(source, aging, nowEpochMillis, lots, wood, contaminationLoad,
                targets, variationSeed, Map.of());
    }

    public AgingResult finish(FermentedProcessDraft source, AgingBatchState aging,
            long nowEpochMillis, Map<String, IngredientLot> lots,
            WoodCharacteristics wood, double contaminationLoad,
            QualityTargets targets, long variationSeed,
            Map<String, Double> additives) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(aging);
        Objects.requireNonNull(lots);
        Objects.requireNonNull(wood);
        Objects.requireNonNull(targets);
        Objects.requireNonNull(additives);

        DistillationControls controls = source.distillery().controls();
        var process = new ProcessProfile(
                source.distillery().ingredientRatios(),
                source.distillery().preparationMethod(),
                source.fermentation(),
                new DistillationProfile(controls.headsCutPercent(), controls.heartsCutPercent(),
                        controls.tailsCutPercent(), controls.temperatureCelsius(), controls.passes()),
                aging.settings().profile(aging.elapsedSeconds(nowEpochMillis)),
                additives
        );
        var conditions = new BatchConditions(lots, contaminationLoad, 90.0, wood);
        var simulation = calculator.calculate(process, targets, conditions, BatchVariation.fromSeed(variationSeed));
        return new AgingResult(process, simulation);
    }
}
