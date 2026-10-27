package me.arcanebrewery.arcaneBrewery.application.discovery;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientIds;
import java.util.Objects;
public record MaterialKey(Kind kind,String registryId) {
    public enum Kind { INGREDIENT, CATALYST }
    public MaterialKey {Objects.requireNonNull(kind);registryId=IngredientIds.canonical(registryId);}
}
