package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

/** Suitability for this recipe, flavor extraction and preservation, each on a 0..100 scale. */
public record WoodCharacteristics(double suitability, double flavorContribution, double preservation) {
    public WoodCharacteristics {
        suitability = DomainValues.percent(suitability);
        flavorContribution = DomainValues.percent(flavorContribution);
        preservation = DomainValues.percent(preservation);
    }
}
