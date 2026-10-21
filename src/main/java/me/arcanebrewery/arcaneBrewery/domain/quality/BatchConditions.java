package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Measured ingredient lots plus incoming contamination and catalyst stability (0..100). */
public record BatchConditions(Map<String, IngredientLot> ingredients, double contaminationLoad,
        double catalystStability, WoodCharacteristics wood) {
    public BatchConditions {
        Objects.requireNonNull(ingredients, "ingredients");
        TreeMap<String, IngredientLot> copy = new TreeMap<>();
        ingredients.forEach((id, lot) -> {
            String key = DomainValues.id(id);
            Objects.requireNonNull(lot, "lot");
            if (!key.equals(DomainValues.id(lot.composition().id())))
                throw new IllegalArgumentException("lot id must match ingredient id");
            if (copy.putIfAbsent(key, lot) != null)
                throw new IllegalArgumentException("duplicate ingredient id");
        });
        ingredients = Collections.unmodifiableMap(copy);
        contaminationLoad = DomainValues.percent(contaminationLoad);
        catalystStability = DomainValues.percent(catalystStability);
        Objects.requireNonNull(wood, "wood");
    }
}
