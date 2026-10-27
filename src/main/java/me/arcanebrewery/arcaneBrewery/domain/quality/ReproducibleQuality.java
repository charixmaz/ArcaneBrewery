package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import java.util.*;

/** Fantasy gameplay model V2. No random quality roll; no potency term in workmanship. */
public final class ReproducibleQuality {
    private static double bound(double v) { return Math.max(0,Math.min(100,v)); }
    public QualityAssessment assess(ProcessProfile p,BatchConditions c,long maturityTarget) {
        if(maturityTarget<=0) throw new IllegalArgumentException("maturity target must be positive");
        if(!c.ingredients().keySet().equals(p.ingredientRatios().keySet())) throw new IllegalArgumentException("ingredient lots mismatch");
        var culture=BrewingCulture.of(p.fermentation().yeastOrCatalyst());
        double purity=p.ingredientRatios().entrySet().stream().mapToDouble(e->e.getValue()*c.ingredients().get(e.getKey()).purity()).sum();
        var f=p.fermentation();var d=p.distillation();var a=p.aging();
        double temperature=bound(100-8*Math.abs(f.temperatureCelsius()-culture.temperature()));
        double duration=bound(100-100*Math.abs((double)f.durationSeconds()/culture.seconds()-1));
        double extraction=bound(100-2*Math.abs(d.targetTemperatureCelsius()-78)
                -3*Math.max(0,10-d.headsCutPercent())-2*Math.max(0,10-d.tailsCutPercent())
                -Math.abs(d.heartsCutPercent()-80)*.5);
        double hygiene=bound(Math.min(f.sanitation(),a.caskCleanliness())-f.oxygenExposure()*.5-a.oxidation()*.5);
        double maturationDeviation=a.durationSeconds()<maturityTarget
                ?1-(double)a.durationSeconds()/maturityTarget:Math.max(0,(double)a.durationSeconds()-maturityTarget-15)/maturityTarget;
        double maturation=bound(100-100*maturationDeviation
                -Math.abs(a.storageTemperatureCelsius()-18)*2);
        double stability=p.alchemicalAdditives().isEmpty()?100:c.catalystStability();
        double sugar=p.ingredientRatios().entrySet().stream().mapToDouble(e->e.getValue()*c.ingredients().get(e.getKey()).composition().sugar()).sum();
        double starch=p.ingredientRatios().entrySet().stream().mapToDouble(e->e.getValue()*c.ingredients().get(e.getKey()).composition().starch()).sum();
        double composition=bound(2*(culture==BrewingCulture.LUMINOUS?sugar:culture==BrewingCulture.QUIET?starch:sugar+starch));
        var factors=List.of(new QualityAssessment.Factor("Ingredient purity",purity,.20,"wash supported ingredients; use excellent lots"),
                new QualityAssessment.Factor("Temperature suitability",temperature,.20,"culture ideal "+culture.temperature()+" C"),
                new QualityAssessment.Factor("Cultivation duration",duration,.15,"culture ideal "+culture.seconds()+" s"),
                new QualityAssessment.Factor("Extraction",extraction,.15,"ideal 78 C and balanced cuts"),
                new QualityAssessment.Factor("Sanitation and storage",hygiene,.10,"sanitation, oxygen, cleanliness and oxidation"),
                new QualityAssessment.Factor("Maturation",maturation,.10,"compare harvest times and record successful batches"),
                new QualityAssessment.Factor("Catalyst stability",stability,.05,"additional catalyst can reduce stability"),
                new QualityAssessment.Factor("Formulation suitability",composition,.05,"WILD: sugar+starch >=50; LUMINOUS: sugar >=50; QUIET: starch >=50"));
        double penalty=bound(c.contaminationLoad()*.5+p.ingredientRatios().entrySet().stream().mapToDouble(e->e.getValue()*c.ingredients().get(e.getKey()).composition().toxicity()).sum()*.5+Math.max(0,10-d.headsCutPercent())*2+Math.max(0,10-d.tailsCutPercent()));
        double score=bound(factors.stream().mapToDouble(x->x.score()*x.weight()).sum()-penalty);
        // Maturation is a prerequisite, not merely a 10% contribution.
        // Unfinished batches are unusable; severe over-aging can ruin an otherwise perfect brew.
        double readiness=a.durationSeconds()<maturityTarget?0:maturation/100.0;
        score=bound(score*readiness);
        var assessed=new ArrayList<>(factors);
        assessed.add(new QualityAssessment.Factor("Maturation gate",readiness*100,0,
            a.durationSeconds()<maturityTarget?"unfinished: quality is zero until maturity":"final quality multiplied by maturation suitability"));
        return new QualityAssessment(assessed,penalty,score);
    }
}
