package me.arcanebrewery.arcaneBrewery.domain.ingredient;

public record IngredientModifiers(double flavor, double potency) {
    public static final IngredientModifiers NONE = new IngredientModifiers(0, 0);
    public IngredientModifiers {
        for (double value : new double[]{flavor, potency}) {
            if (!Double.isFinite(value) || Math.abs(value) > 10)
                throw new IllegalArgumentException("ingredient modifiers must be within -10..10");
        }
    }
}
