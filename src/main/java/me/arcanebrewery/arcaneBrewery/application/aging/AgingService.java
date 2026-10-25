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
        return finish(source,aging,nowEpochMillis,lots,wood,contaminationLoad,targets,variationSeed,additives,100);
    }
    public AgingResult finish(FermentedProcessDraft source,AgingBatchState aging,long nowEpochMillis,
            Map<String,IngredientLot> lots,WoodCharacteristics wood,double contaminationLoad,QualityTargets targets,
            long variationSeed,Map<String,Double> additives,double capturedCatalystStability) {
        if(!Double.isFinite(capturedCatalystStability)||capturedCatalystStability<0||capturedCatalystStability>100) throw new IllegalArgumentException("invalid catalyst stability");
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
                aging.settings().profile(source.distillery().preparationMethod().equals("distillation-v2")
                        ?aging.elapsedSeconds(nowEpochMillis):identityAgingSeconds(aging.elapsedSeconds(nowEpochMillis), aging.maturationSeconds())),
                additives
        );
        var conditions = new BatchConditions(lots, contaminationLoad, source.distillery().preparationMethod().equals("distillation-v2")
                ?Math.min(capturedCatalystStability,additives.isEmpty()?100:Math.max(0,100-additives.values().stream().mapToDouble(Double::doubleValue).sum()*5)):90, wood);
        var simulation = calculator.calculate(process, targets, conditions, BatchVariation.fromSeed(variationSeed));
        if(source.distillery().preparationMethod().equals("distillation-v2")) {
            var assessment=new ReproducibleQuality().assess(process,conditions,aging.maturationSeconds());
            var q=simulation.quality();
            var culture=BrewingCulture.of(process.fermentation().yeastOrCatalyst());
            double cuts=process.distillation().headsCutPercent()<10?1.2:process.distillation().headsCutPercent()>10?.8:1;
            var quality=new BatchQuality(q.purity(),q.flavor(),q.aroma(),q.stability(),q.contamination(),
                q.magicalPotency()*culture.potency()*cuts,assessment.score());
            return new AgingResult(process,new BatchSimulationResult(simulation.fingerprint(),quality,simulation.contamination()),assessment);
        }
        return new AgingResult(process, simulation);
    }

    static long identityAgingSeconds(long elapsedSeconds, long maturitySeconds) {
        if (elapsedSeconds < 0 || maturitySeconds < 0) throw new IllegalArgumentException("invalid aging duration");
        if (elapsedSeconds < maturitySeconds) return elapsedSeconds;
        long stageSeconds = 30L;
        return maturitySeconds + ((elapsedSeconds - maturitySeconds) / stageSeconds) * stageSeconds;
    }
}
