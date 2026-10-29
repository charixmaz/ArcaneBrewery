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
        return finish(source,aging,nowEpochMillis,lots,wood,contaminationLoad,targets,variationSeed,additives,capturedCatalystStability,2);
    }
    public AgingResult finish(FermentedProcessDraft source,AgingBatchState aging,long nowEpochMillis,
            Map<String,IngredientLot> lots,WoodCharacteristics wood,double contaminationLoad,QualityTargets targets,
            long variationSeed,Map<String,Double> additives,double capturedCatalystStability,int qualityGeneration) {
        if(qualityGeneration!=2&&qualityGeneration!=3)throw new IllegalArgumentException("Unsupported quality policy");
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
                ?capturedCatalystStability:90, wood);
        var f=process.fermentation();var a=process.aging();
        var measured=new ProcessProfile(process.ingredientRatios(),process.preparationMethod(),
            new me.arcanebrewery.arcaneBrewery.domain.process.FermentationProfile(f.yeastOrCatalyst(),f.temperatureCelsius(),f.durationSeconds(),100,0),process.distillation(),
            new me.arcanebrewery.arcaneBrewery.domain.process.AgingProfile(a.woodType(),a.durationSeconds(),0,a.storageTemperatureCelsius(),100),process.alchemicalAdditives());
        var activeConditions=new BatchConditions(lots,0,conditions.catalystStability(),wood);
        var calculated=calculator.calculateWithoutContamination(measured,targets,activeConditions,BatchVariation.fromSeed(variationSeed));
        var cq=calculated.quality();
        var simulation=new BatchSimulationResult(me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint.from(process),
            new BatchQuality(cq.purity(),cq.flavor(),cq.aroma(),cq.stability(),0,cq.magicalPotency()),calculated.contamination());
        if(source.distillery().preparationMethod().equals("distillation-v2")) {
            var assessment=qualityGeneration==3?new CombinationQuality().assess(process,conditions,aging.maturationSeconds()):new ReproducibleQuality().assess(process,conditions,aging.maturationSeconds());
            var q=simulation.quality();
            var culture=BrewingCulture.of(process.fermentation().yeastOrCatalyst());
            double cuts=process.distillation().headsCutPercent()<10?1.2:process.distillation().headsCutPercent()>10?.8:1;
            var quality=new BatchQuality(q.purity(),q.flavor(),q.aroma(),q.stability(),q.contamination(),
                q.magicalPotency()*culture.potency()*cuts,assessment.score());
            if(aging.recipeTiming() && RecipeMaturation.poisonous(aging.elapsedSeconds(nowEpochMillis))) {
                quality=new BatchQuality(q.purity(),q.flavor(),q.aroma(),q.stability(),100,0,0.0);
                assessment=new QualityAssessment(java.util.List.of(new QualityAssessment.Factor("Premature withdrawal",0,1,
                    "first 60 seconds: poisonous unfinished batch; no discovery")),0,0);
            }
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
