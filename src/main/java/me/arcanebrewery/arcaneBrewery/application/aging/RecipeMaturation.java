package me.arcanebrewery.arcaneBrewery.application.aging;

import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentedProcessDraft;
import me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import java.util.Map;

/** Stable pseudo-random recipe balance V1. Never salt with player, batch, world or clock. */
public final class RecipeMaturation {
    private RecipeMaturation() { }
    public static long target(FermentedProcessDraft source, Map<String,Double> additives) {
        var d=source.distillery(); var controls=d.controls();
        var p=new ProcessProfile(d.ingredientRatios(),d.preparationMethod(),source.fermentation(),
                new DistillationProfile(controls.headsCutPercent(),controls.heartsCutPercent(),controls.tailsCutPercent(),controls.temperatureCelsius(),controls.passes()),
                new AgingProfile("none",0,0,18,100),additives);
        return 120 + Long.parseUnsignedLong(FormulationIdentity.from(p).value().substring(0,8),16)%181;
    }
    public static long targetV3(FermentedProcessDraft source,Map<String,Double> additives,
            Map<String,me.arcanebrewery.arcaneBrewery.domain.quality.IngredientLot> lots) {
        var d=source.distillery();var c=d.controls();
        var p=new ProcessProfile(d.ingredientRatios(),d.preparationMethod(),source.fermentation(),
            new DistillationProfile(c.headsCutPercent(),c.heartsCutPercent(),c.tailsCutPercent(),c.temperatureCelsius(),c.passes()),
            new AgingProfile("none",0,0,18,100),additives);
        return new me.arcanebrewery.arcaneBrewery.domain.quality.CombinationQuality().targets(p,lots).maturation();
    }
    public static boolean poisonous(long elapsedSeconds) { return elapsedSeconds<=60; }
}
