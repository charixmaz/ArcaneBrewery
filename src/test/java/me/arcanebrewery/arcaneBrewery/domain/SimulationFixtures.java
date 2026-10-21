package me.arcanebrewery.arcaneBrewery.domain;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import java.util.Map;

public final class SimulationFixtures {
    private SimulationFixtures() {}
    public static final QualityTargets TARGETS = new QualityTargets(22, 3600, 7200, 15, 15);
    public static final WoodCharacteristics WOOD = new WoodCharacteristics(90, 80, 90);

    public static ProcessProfile process() {
        return new ProcessProfile(Map.of("wheat", 0.65, "honey", 0.35), "mash",
                fermentation(22, 3600, 100, 0), distillation(10, 80, 10, 1),
                aging("oak", 7200, 0, 15, 100), Map.of("moon dust", 0.1));
    }
    public static FermentationProfile fermentation(double temp, long seconds, double sanitation, double oxygen) {
        return new FermentationProfile("wild yeast", temp, seconds, sanitation, oxygen);
    }
    public static DistillationProfile distillation(double heads, double hearts, double tails, int passes) {
        return new DistillationProfile(heads, hearts, tails, 78, passes);
    }
    public static AgingProfile aging(String wood, long seconds, double oxidation, double temp, double cleanliness) {
        return new AgingProfile(wood, seconds, oxidation, temp, cleanliness);
    }
    public static ProcessProfile withFermentation(FermentationProfile f) {
        var p = process();
        return new ProcessProfile(p.ingredientRatios(), p.preparationMethod(), f, p.distillation(),
                p.aging(), p.alchemicalAdditives());
    }
    public static ProcessProfile withAging(AgingProfile a) {
        var p = process();
        return new ProcessProfile(p.ingredientRatios(), p.preparationMethod(), p.fermentation(),
                p.distillation(), a, p.alchemicalAdditives());
    }
    public static ProcessProfile withDistillation(DistillationProfile d) {
        var p = process();
        return new ProcessProfile(p.ingredientRatios(), p.preparationMethod(), p.fermentation(),
                d, p.aging(), p.alchemicalAdditives());
    }
    public static BatchConditions conditions(double contamination, double purity, double stability,
            WoodCharacteristics wood) {
        return new BatchConditions(Map.of(
                "wheat", new IngredientLot(new IngredientProfile("wheat", 50, 70, 25, 20, 70, 0, 60), purity),
                "honey", new IngredientLot(new IngredientProfile("honey", 90, 0, 30, 5, 90, 0, 80), purity)),
                contamination, stability, wood);
    }
    public static BatchConditions clean() { return conditions(0, 95, 95, WOOD); }
}
