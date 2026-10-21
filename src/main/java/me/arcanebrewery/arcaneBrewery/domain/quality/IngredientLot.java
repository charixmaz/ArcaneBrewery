package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientProfile;
import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;
import java.util.Objects;

public record IngredientLot(IngredientProfile composition, double purity) {
    public IngredientLot {
        Objects.requireNonNull(composition, "composition");
        purity = DomainValues.percent(purity);
    }
}
