package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

public record BatchQuality(
        double purity,
        double flavor,
        double aroma,
        double stability,
        double contamination,
        double magicalPotency,
        Double workmanship
) {
    public BatchQuality(double purity,double flavor,double aroma,double stability,double contamination,double magicalPotency) {
        this(purity,flavor,aroma,stability,contamination,magicalPotency,null);
    }
    public BatchQuality {
        if(workmanship!=null) workmanship=clamp(workmanship);
        purity = clamp(purity);
        flavor = clamp(flavor);
        aroma = clamp(aroma);
        stability = clamp(stability);
        contamination = clamp(contamination);
        magicalPotency = clamp(magicalPotency);
    }

    public double overallScore() {
        if(workmanship!=null) return workmanship;
        double positive = purity * 0.28 + flavor * 0.26 + aroma * 0.16 + stability * 0.18 + magicalPotency * 0.12;
        double penalty = contamination * 0.45;
        return clamp(positive - penalty);
    }

    private static double clamp(double value) {
        return DomainValues.percent(value);
    }
}
