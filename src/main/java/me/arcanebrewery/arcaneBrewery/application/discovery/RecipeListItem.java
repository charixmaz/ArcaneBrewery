package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;

/** Catalog-safe projection: deliberately contains no exact process. */
public record RecipeListItem(String name, DrinkFingerprint fingerprint) { }
