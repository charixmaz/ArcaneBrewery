package me.arcanebrewery.arcaneBrewery.application.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import java.math.*;
import java.util.*;

/** Decimal weighted sums avoid slot-order-dependent floating-point accumulation. */
public final class BatchIngredientExtractor {
    private final IngredientRegistry registry;
    public BatchIngredientExtractor(IngredientRegistry registry) { this.registry = Objects.requireNonNull(registry); }

    public ExtractedIngredients extract(Collection<? extends IngredientInput> inputs) {
        var grouped = new TreeMap<String, Accumulator>();
        var containers = new TreeMap<String, Long>();
        var liquids = new TreeMap<String, Long>();
        for (var input : inputs) {
            Objects.requireNonNull(input);
            switch (input) {
                case IngredientInput.Ingredient ingredient -> {
                    var instance = ingredient.instance();
                    registry.require(instance.ingredientId());
                    grouped.computeIfAbsent(instance.ingredientId(), ignored -> new Accumulator()).add(instance);
                }
                case IngredientInput.Container container -> containers.merge(container.id(), container.quantity(), Math::addExact);
                case IngredientInput.BaseLiquid liquid -> liquids.merge(liquid.id(), liquid.quantity(), Math::addExact);
                case IngredientInput.Empty ignored -> { }
            }
        }
        var portions = new TreeMap<String, ExtractedIngredients.Portion>();
        grouped.forEach((id, a) -> portions.put(id, new ExtractedIngredients.Portion(registry.require(id).profile(),
                a.quantity, a.average(a.purity), new IngredientModifiers(a.average(a.flavor), a.average(a.potency)), a.sources)));
        return new ExtractedIngredients(portions, containers, liquids);
    }

    private static final class Accumulator {
        long quantity;
        BigDecimal purity = BigDecimal.ZERO, flavor = BigDecimal.ZERO, potency = BigDecimal.ZERO;
        final Map<Optional<IngredientProvenance>, Long> sources = new HashMap<>();
        void add(IngredientInstance instance) {
            quantity = Math.addExact(quantity, instance.quantity());
            var amount = BigDecimal.valueOf(instance.quantity());
            purity = purity.add(amount.multiply(BigDecimal.valueOf(instance.purity())));
            flavor = flavor.add(amount.multiply(BigDecimal.valueOf(instance.modifiers().flavor())));
            potency = potency.add(amount.multiply(BigDecimal.valueOf(instance.modifiers().potency())));
            sources.merge(instance.provenance(), instance.quantity(), Math::addExact);
        }
        double average(BigDecimal value) { return value.divide(BigDecimal.valueOf(quantity), MathContext.DECIMAL128).doubleValue(); }
    }
}
