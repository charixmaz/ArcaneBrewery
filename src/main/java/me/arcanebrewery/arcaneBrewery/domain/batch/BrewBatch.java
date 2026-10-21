package me.arcanebrewery.arcaneBrewery.domain.batch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BrewBatch {

    private final UUID id;
    private final UUID ownerId;
    private final Map<String, Double> ingredientRatios;
    private final long createdAtEpochMillis;

    public BrewBatch(UUID id, UUID ownerId, Map<String, Double> ingredientRatios, long createdAtEpochMillis) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ingredientRatios, "ingredientRatios");
        if (ingredientRatios.isEmpty()) throw new IllegalArgumentException("ingredientRatios must not be empty");

        double total = ingredientRatios.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) throw new IllegalArgumentException("ingredient ratio total must be positive");

        Map<String, Double> normalized = new LinkedHashMap<>();
        ingredientRatios.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (entry.getKey() == null || entry.getKey().isBlank()) {
                        throw new IllegalArgumentException("ingredient id must not be blank");
                    }
                    if (entry.getValue() == null || entry.getValue() < 0.0) {
                        throw new IllegalArgumentException("ingredient ratio must be non-negative");
                    }
                    normalized.put(entry.getKey(), entry.getValue() / total);
                });

        this.ingredientRatios = Collections.unmodifiableMap(normalized);
        this.createdAtEpochMillis = createdAtEpochMillis;
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public Map<String, Double> ingredientRatios() { return ingredientRatios; }
    public long createdAtEpochMillis() { return createdAtEpochMillis; }
}
