package me.arcanebrewery.arcaneBrewery.application.distillery;

import me.arcanebrewery.arcaneBrewery.application.ingredient.ExtractedIngredients;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.IngredientIds;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record DistilleryProcessDraft(
        Map<String, Double> ingredientRatios,
        Map<String, Double> ingredientPurity,
        String preparationMethod,
        DistillationControls controls
) {
    public DistilleryProcessDraft {
        Objects.requireNonNull(ingredientRatios, "ingredientRatios");
        Objects.requireNonNull(ingredientPurity, "ingredientPurity");
        Objects.requireNonNull(preparationMethod, "preparationMethod");
        Objects.requireNonNull(controls, "controls");
        if (ingredientRatios.isEmpty()) throw new IllegalArgumentException("draft has no ingredients");

        var ratios = new TreeMap<String, Double>();
        ingredientRatios.forEach((id, value) -> {
            String canonical = IngredientIds.canonical(id);
            if (!canonical.equals(id) || value == null || !Double.isFinite(value) || value <= 0 || value > 1) {
                throw new IllegalArgumentException("invalid ingredient ratio");
            }
            ratios.put(id, value);
        });
        var purity = new TreeMap<String, Double>();
        ingredientPurity.forEach((id, value) -> {
            String canonical = IngredientIds.canonical(id);
            if (!canonical.equals(id) || value == null || !Double.isFinite(value) || value < 0 || value > 100) {
                throw new IllegalArgumentException("invalid ingredient purity");
            }
            purity.put(id, value);
        });
        if (!purity.keySet().equals(ratios.keySet())) throw new IllegalArgumentException("purity keys must match ratios");
        double total = ratios.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 1.0) > 1e-9) throw new IllegalArgumentException("ingredient ratios must sum to 1");
        preparationMethod = preparationMethod.strip().toLowerCase(java.util.Locale.ROOT);
        if (preparationMethod.isEmpty()) throw new IllegalArgumentException("preparation method is blank");
        ingredientRatios = Collections.unmodifiableMap(ratios);
        ingredientPurity = Collections.unmodifiableMap(purity);
    }

    public static DistilleryProcessDraft from(ExtractedIngredients extracted, DistillationControls controls) {
        var purity = new TreeMap<String, Double>();
        extracted.ingredients().forEach((id, portion) -> purity.put(id, portion.purity()));
        return new DistilleryProcessDraft(extracted.ratios(), purity, "distillation", controls);
    }
}
