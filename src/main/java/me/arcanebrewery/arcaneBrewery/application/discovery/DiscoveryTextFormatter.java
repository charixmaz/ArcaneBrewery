package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Plain-text formatter; Bukkit adapters may add color codes around these lines. */
public final class DiscoveryTextFormatter {
    public List<String> format(RecipeKnowledge knowledge) {
        return switch (knowledge) {
            case RecipeKnowledge.Unknown ignored -> List.of("Unknown recipe.");
            case RecipeKnowledge.Identified identified -> List.of(
                    "Name: " + identified.discovery().displayName().value(),
                    "Knowledge: IDENTIFIED",
                    "Fingerprint: " + identified.discovery().fingerprint().value(),
                    "Original discoverer: " + identified.discovery().discovererId()
            );
            case RecipeKnowledge.Partial partial -> formatPartial(partial);
            case RecipeKnowledge.Full full -> formatFull(full);
        };
    }

    private List<String> formatPartial(RecipeKnowledge.Partial partial) {
        var lines = new ArrayList<String>();
        lines.add("Name: " + partial.discovery().displayName().value());
        lines.add("Knowledge: PARTIAL");
        lines.add("Fingerprint: " + partial.discovery().fingerprint().value());
        partial.disclosure().fields().forEach((field, value) ->
                lines.add(label(field) + ": " + detail(value)));
        return List.copyOf(lines);
    }

    private List<String> formatFull(RecipeKnowledge.Full full) {
        ProcessProfile p = full.process();
        var lines = new ArrayList<String>();
        lines.add("Name: " + full.discovery().displayName().value());
        lines.add("Knowledge: FULL");
        lines.add("Fingerprint: " + full.discovery().fingerprint().value());
        lines.add("Ingredients: " + amounts(p.ingredientRatios()));
        lines.add("Preparation: " + p.preparationMethod());
        lines.add("Fermentation catalyst: " + p.fermentation().yeastOrCatalyst());
        lines.add("Fermentation temperature: " + decimal(p.fermentation().temperatureCelsius()) + " C");
        lines.add("Fermentation duration: " + p.fermentation().durationSeconds() + " s");
        lines.add("Distillation cuts: " + decimal(p.distillation().headsCutPercent()) + "/"
                + decimal(p.distillation().heartsCutPercent()) + "/"
                + decimal(p.distillation().tailsCutPercent()));
        lines.add("Distillation temperature: " + decimal(p.distillation().targetTemperatureCelsius()) + " C");
        lines.add("Distillation passes: " + p.distillation().passes());
        lines.add("Aging wood: " + p.aging().woodType());
        lines.add("Aging duration: " + p.aging().durationSeconds() + " s");
        if (!p.alchemicalAdditives().isEmpty()) {
            lines.add("Alchemical additives: " + amounts(p.alchemicalAdditives()));
        }
        return List.copyOf(lines);
    }

    private String detail(RecipeDisclosure.Detail detail) {
        return switch (detail) {
            case RecipeDisclosure.Text text -> text.value();
            case RecipeDisclosure.Decimal decimal -> decimal(decimal.value());
            case RecipeDisclosure.Count count -> Long.toString(count.value());
            case RecipeDisclosure.Amounts amounts -> amounts(amounts.values());
            case RecipeDisclosure.Cuts cuts -> decimal(cuts.heads()) + "/" + decimal(cuts.hearts())
                    + "/" + decimal(cuts.tails());
        };
    }

    private String amounts(Map<String, Double> values) {
        return values.entrySet().stream()
                .map(e -> e.getKey() + "=" + decimal(e.getValue()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    private String label(ProcessField field) {
        return field.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
