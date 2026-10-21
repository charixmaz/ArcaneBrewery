package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

public record BatchQuality(
        double purity,
        double flavor,
        double aroma,
        double stability,
        double contamination,
        double magicalPotency
) {
    public BatchQuality {
        purity = clamp(purity);
        flavor = clamp(flavor);
        aroma = clamp(aroma);
        stability = clamp(stability);
        contamination = clamp(contamination);
        magicalPotency = clamp(magicalPotency);
    }

    public double overallScore() {
        double positive = purity * 0.28 + flavor * 0.26 + aroma * 0.16 + stability * 0.18 + magicalPotency * 0.12;
        double penalty = contamination * 0.45;
        return clamp(positive - penalty);
    }

    private static double clamp(double value) {
        return DomainValues.percent(value);
    }
}
