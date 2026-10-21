package me.arcanebrewery.arcaneBrewery.application.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import java.util.Objects;

public sealed interface IngredientInput {
    record Ingredient(IngredientInstance instance) implements IngredientInput {
        public Ingredient { Objects.requireNonNull(instance); }
    }
    record Container(String id, long quantity) implements IngredientInput {
        public Container {
            id = IngredientIds.canonical(id);
            if (quantity <= 0) throw new IllegalArgumentException("container quantity must be positive");
        }
    }
    record BaseLiquid(String id, long quantity) implements IngredientInput {
        public BaseLiquid {
            id = IngredientIds.canonical(id);
            if (quantity <= 0) throw new IllegalArgumentException("liquid quantity must be positive");
        }
    }
    record Empty() implements IngredientInput {}
}
