package me.arcanebrewery.arcaneBrewery.domain.process;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Canonical recipe process. Ingredient amounts are relative; additive amounts are recipe doses. */
public final class ProcessProfile {
    private final Map<String, Double> ingredientRatios;
    private final String preparationMethod;
    private final FermentationProfile fermentation;
    private final DistillationProfile distillation;
    private final AgingProfile aging;
    private final Map<String, Double> alchemicalAdditives;

    public ProcessProfile(Map<String, Double> ingredientRatios, String preparationMethod,
            FermentationProfile fermentation, DistillationProfile distillation, AgingProfile aging,
            Map<String, Double> alchemicalAdditives) {
        this(ingredientRatios, preparationMethod, fermentation, distillation, aging, alchemicalAdditives, false);
    }

    private ProcessProfile(Map<String, Double> ingredientRatios, String preparationMethod,
            FermentationProfile fermentation, DistillationProfile distillation, AgingProfile aging,
            Map<String, Double> alchemicalAdditives, boolean canonical) {
        this.ingredientRatios = canonical ? validateCanonicalRatios(ingredientRatios) : canonicalAmounts(ingredientRatios, true);
        this.preparationMethod = DomainValues.id(preparationMethod);
        this.fermentation = Objects.requireNonNull(fermentation, "fermentation");
        this.distillation = Objects.requireNonNull(distillation, "distillation");
        this.aging = Objects.requireNonNull(aging, "aging");
        this.alchemicalAdditives = canonicalAmounts(alchemicalAdditives, false);
    }

    /** Rehydrate already-normalized ratios without re-normalizing their IEEE-754 representation. */
    public static ProcessProfile restoreCanonical(Map<String, Double> ingredientRatios, String preparationMethod,
            FermentationProfile fermentation, DistillationProfile distillation, AgingProfile aging,
            Map<String, Double> alchemicalAdditives) {
        return new ProcessProfile(ingredientRatios, preparationMethod, fermentation, distillation,
                aging, alchemicalAdditives, true);
    }

    private static Map<String, Double> validateCanonicalRatios(Map<String, Double> input) {
        Objects.requireNonNull(input, "ingredientRatios");
        TreeMap<String, Double> copy = new TreeMap<>();
        input.forEach((id, amount) -> {
            if (!DomainValues.id(id).equals(id)) throw new IllegalArgumentException("noncanonical ingredient id");
            copy.put(id, DomainValues.nonNegative(Objects.requireNonNull(amount)));
        });
        double total = copy.values().stream().mapToDouble(Double::doubleValue).sum();
        if (copy.isEmpty() || !Double.isFinite(total) || Math.abs(total - 1) > 1e-12)
            throw new IllegalArgumentException("canonical ratios must sum to one");
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Double> ingredientRatios() { return ingredientRatios; }
    public String preparationMethod() { return preparationMethod; }
    public FermentationProfile fermentation() { return fermentation; }
    public DistillationProfile distillation() { return distillation; }
    public AgingProfile aging() { return aging; }
    public Map<String, Double> alchemicalAdditives() { return alchemicalAdditives; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProcessProfile p && ingredientRatios.equals(p.ingredientRatios)
                && preparationMethod.equals(p.preparationMethod) && fermentation.equals(p.fermentation)
                && distillation.equals(p.distillation) && aging.equals(p.aging)
                && alchemicalAdditives.equals(p.alchemicalAdditives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ingredientRatios, preparationMethod, fermentation, distillation, aging, alchemicalAdditives);
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
