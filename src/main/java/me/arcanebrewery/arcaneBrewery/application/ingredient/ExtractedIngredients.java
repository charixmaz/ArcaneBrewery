package me.arcanebrewery.arcaneBrewery.application.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.IngredientLot;
import java.math.*;
import java.util.*;

/** Ingredient quantities/purity, container counts and liquid item units stay separate. */
public record ExtractedIngredients(Map<String, Portion> ingredients, Map<String, Long> containers,
        Map<String, Long> baseLiquids) {
    public record Portion(IngredientProfile composition, long quantity, double purity,
            IngredientModifiers modifiers, Map<Optional<IngredientProvenance>, Long> provenance) {
        public Portion {
            Objects.requireNonNull(composition);
            if (quantity <= 0 || !Double.isFinite(purity) || purity < 0 || purity > 100)
                throw new IllegalArgumentException("invalid extracted portion");
            Objects.requireNonNull(modifiers);
            var sources = new TreeMap<Optional<IngredientProvenance>, Long>(Comparator.comparing(source ->
                    source.map(p -> p.source() + "|" + p.producer().map(UUID::toString).orElse("")).orElse("")));
            long total = 0;
            for (var entry : provenance.entrySet()) {
                Objects.requireNonNull(entry.getKey());
                if (entry.getValue() <= 0) throw new IllegalArgumentException("invalid provenance quantity");
                total = Math.addExact(total, entry.getValue());
                sources.put(entry.getKey(), entry.getValue());
            }
            if (total != quantity) throw new IllegalArgumentException("provenance quantity mismatch");
            provenance = Collections.unmodifiableMap(sources);
        }
    }
    public ExtractedIngredients {
        if (ingredients.isEmpty()) throw new IllegalArgumentException("batch has no ingredients");
        ingredients.forEach((id, portion) -> {
            if (!id.equals(portion.composition().id())) throw new IllegalArgumentException("ingredient key mismatch");
        });
        ingredients = Collections.unmodifiableMap(new TreeMap<>(ingredients));
        containers = counts(containers);
        baseLiquids = counts(baseLiquids);
    }

    public Map<String, Double> ratios() {
        BigDecimal total = ingredients.values().stream().map(p -> BigDecimal.valueOf(p.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var ratios = new TreeMap<String, Double>();
        ingredients.forEach((id, portion) -> ratios.put(id,
                BigDecimal.valueOf(portion.quantity()).divide(total, MathContext.DECIMAL128).doubleValue()));
        return Collections.unmodifiableMap(ratios);
    }

    public Map<String, IngredientLot> simulationLots() {
        var lots = new TreeMap<String, IngredientLot>();
        ingredients.forEach((id, portion) -> lots.put(id, new IngredientLot(portion.composition(), portion.purity())));
        return Collections.unmodifiableMap(lots);
    }

    private static Map<String, Long> counts(Map<String, Long> input) {
        input.forEach((id, quantity) -> {
            if (!IngredientIds.canonical(id).equals(id) || quantity <= 0)
                throw new IllegalArgumentException("invalid noningredient count");
        });
        return Collections.unmodifiableMap(new TreeMap<>(input));
    }
}
