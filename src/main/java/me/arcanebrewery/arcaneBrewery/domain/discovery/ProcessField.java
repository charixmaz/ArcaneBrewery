package me.arcanebrewery.arcaneBrewery.domain.discovery;

/** Identity-bearing recipe fields that an owner may selectively disclose. */
public enum ProcessField {
    INGREDIENT_RATIOS, PREPARATION_METHOD,
    FERMENTATION_CATALYST, FERMENTATION_TEMPERATURE, FERMENTATION_DURATION,
    DISTILLATION_CUTS, DISTILLATION_TEMPERATURE, DISTILLATION_PASSES,
    AGING_WOOD, AGING_DURATION, ALCHEMICAL_ADDITIVES
}
