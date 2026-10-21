package me.arcanebrewery.arcaneBrewery.domain.ingredient;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record IngredientProvenance(String source, Optional<UUID> producer) {
    public IngredientProvenance {
        source = IngredientIds.canonical(source);
        Objects.requireNonNull(producer, "producer");
    }
}
