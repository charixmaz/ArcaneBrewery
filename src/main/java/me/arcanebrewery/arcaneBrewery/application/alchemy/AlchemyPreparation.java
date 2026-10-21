package me.arcanebrewery.arcaneBrewery.application.alchemy;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record AlchemyPreparation(Map<String, Double> additives) {
    public AlchemyPreparation {
        Objects.requireNonNull(additives, "additives");
        var copy = new TreeMap<String, Double>();
        additives.forEach((id, amount) -> {
            String canonical = DomainValues.id(id);
            if (amount == null || !Double.isFinite(amount) || amount <= 0)
                throw new IllegalArgumentException("additive amount must be positive");
            if (copy.putIfAbsent(canonical, amount) != null)
                throw new IllegalArgumentException("duplicate additive id");
        });
        if (copy.isEmpty()) throw new IllegalArgumentException("at least one additive is required");
        additives = Collections.unmodifiableMap(copy);
    }
}
