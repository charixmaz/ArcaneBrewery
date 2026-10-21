package me.arcanebrewery.arcaneBrewery.domain.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

/** IDs are independent of any particular game API or external item plugin. */
public final class IngredientIds {
    private IngredientIds() {}
    public static String canonical(String id) {
        String value = DomainValues.id(id);
        if (value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("ingredient identifier must be namespaced");
        return value;
    }
}
