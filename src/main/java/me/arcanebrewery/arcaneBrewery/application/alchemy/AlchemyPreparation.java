package me.arcanebrewery.arcaneBrewery.application.alchemy;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record AlchemyPreparation(Map<String, Double> additives) {
    public AlchemyPreparation {
        Objects.requireNonNull(additives, "additives");
        if (additives.size() > 16) throw new IllegalArgumentException("too many additives");
        var copy = new TreeMap<String, Double>();
        additives.forEach((id, amount) -> {
            String canonical = DomainValues.id(id);
            if (canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256)
                throw new IllegalArgumentException("additive identifier too long");
            if (amount == null || !Double.isFinite(amount) || amount <= 0)
                throw new IllegalArgumentException("additive amount must be positive");
            if (copy.putIfAbsent(canonical, amount) != null)
                throw new IllegalArgumentException("duplicate additive id");
        });
        if (copy.isEmpty()) throw new IllegalArgumentException("at least one additive is required");
        additives = Collections.unmodifiableMap(copy);
    }
}
