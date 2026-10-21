package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Contains only copied, explicitly selected values; never retains a ProcessProfile. */
public final class RecipeDisclosure {
    public sealed interface Detail permits Text, Decimal, Count, Amounts, Cuts {}
    public record Text(String value) implements Detail {}
    public record Decimal(double value) implements Detail {}
    public record Count(long value) implements Detail {}
    public record Amounts(Map<String, Double> values) implements Detail {
        public Amounts { values = Map.copyOf(values); }
    }
    public record Cuts(double heads, double hearts, double tails) implements Detail {}

    private final Map<ProcessField, Detail> fields;

    private RecipeDisclosure(Map<ProcessField, Detail> fields) {
        this.fields = Collections.unmodifiableMap(new EnumMap<>(fields));
    }

    public Map<ProcessField, Detail> fields() { return fields; }

    public static RecipeDisclosure from(ProcessProfile process, Set<ProcessField> selection) {
        Objects.requireNonNull(process, "process");
        Set<ProcessField> selected = Set.copyOf(selection);
        if (selected.isEmpty() || selected.size() == ProcessField.values().length)
            throw new IllegalArgumentException("partial disclosure must omit at least one recipe field");
        Map<ProcessField, Detail> values = new EnumMap<>(ProcessField.class);
        for (ProcessField field : selected) {
            Detail value = switch (field) {
                case INGREDIENT_RATIOS -> new Amounts(process.ingredientRatios());
                case PREPARATION_METHOD -> new Text(process.preparationMethod());
                case FERMENTATION_CATALYST -> new Text(process.fermentation().yeastOrCatalyst());
                case FERMENTATION_TEMPERATURE -> new Decimal(process.fermentation().temperatureCelsius());
                case FERMENTATION_DURATION -> new Count(process.fermentation().durationSeconds());
                case DISTILLATION_CUTS -> new Cuts(process.distillation().headsCutPercent(),
                        process.distillation().heartsCutPercent(), process.distillation().tailsCutPercent());
                case DISTILLATION_TEMPERATURE -> new Decimal(process.distillation().targetTemperatureCelsius());
                case DISTILLATION_PASSES -> new Count(process.distillation().passes());
                case AGING_WOOD -> new Text(process.aging().woodType());
                case AGING_DURATION -> new Count(process.aging().durationSeconds());
                case ALCHEMICAL_ADDITIVES -> new Amounts(process.alchemicalAdditives());
            };
            values.put(field, value);
        }
        return new RecipeDisclosure(values);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecipeDisclosure disclosure && fields.equals(disclosure.fields);
    }

    @Override
    public int hashCode() { return fields.hashCode(); }
}
