package me.arcanebrewery.arcaneBrewery.domain.process;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Canonical recipe process. Ingredient amounts are relative; additive amounts are recipe doses. */
public record ProcessProfile(
        Map<String, Double> ingredientRatios,
        String preparationMethod,
        FermentationProfile fermentation,
        DistillationProfile distillation,
        AgingProfile aging,
        Map<String, Double> alchemicalAdditives
) {
    public ProcessProfile {
        ingredientRatios = canonicalAmounts(ingredientRatios, true);
        preparationMethod = DomainValues.id(preparationMethod);
        Objects.requireNonNull(fermentation, "fermentation");
        Objects.requireNonNull(distillation, "distillation");
        Objects.requireNonNull(aging, "aging");
        alchemicalAdditives = canonicalAmounts(alchemicalAdditives, false);
    }

    private static Map<String, Double> canonicalAmounts(Map<String, Double> input, boolean ratios) {
        Objects.requireNonNull(input, "amounts");
        TreeMap<String, BigDecimal> amounts = new TreeMap<>();
        input.forEach((key, value) -> {
            String id = DomainValues.id(key);
            BigDecimal amount = BigDecimal.valueOf(DomainValues.nonNegative(Objects.requireNonNull(value)));
            if (amount.signum() > 0 && amounts.putIfAbsent(id, amount) != null)
                throw new IllegalArgumentException("duplicate normalized id: " + id);
        });
        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ratios && total.signum() == 0) throw new IllegalArgumentException("ingredients must not be empty");
        TreeMap<String, Double> result = new TreeMap<>();
        amounts.forEach((id, amount) -> result.put(id, ratios
                ? amount.divide(total, MathContext.DECIMAL128).doubleValue() : amount.doubleValue()));
        return Collections.unmodifiableMap(result);
    }
}
