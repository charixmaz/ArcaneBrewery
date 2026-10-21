package me.arcanebrewery.arcaneBrewery.application.ingredient;

public final class UnsupportedIngredientException extends IllegalArgumentException {
    public UnsupportedIngredientException(String externalId) { super("Unsupported ingredient: " + externalId); }
}
