package me.arcanebrewery.arcaneBrewery.domain.ingredient;

import java.util.Objects;
import java.util.Optional;

/** One homogeneous stack/lot. Quantity is item units; purity is the item's whole percentage. */
public record IngredientInstance(String ingredientId, int purity, long quantity,
        IngredientModifiers modifiers, Optional<IngredientProvenance> provenance) {
    public IngredientInstance {
        ingredientId = IngredientIds.canonical(ingredientId);
        purity = Math.max(0, Math.min(100, purity));
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(provenance, "provenance");
    }
    public IngredientInstance washed() {
        return new IngredientInstance(ingredientId, Math.min(100, purity + 25), quantity, modifiers, provenance);
    }
    public IngredientInstance withQuantity(long quantity) {
        return new IngredientInstance(ingredientId, purity, quantity, modifiers, provenance);
    }
}
