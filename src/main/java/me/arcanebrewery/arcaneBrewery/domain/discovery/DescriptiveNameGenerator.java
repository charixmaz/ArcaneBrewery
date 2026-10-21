package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/** Broad descriptions, not unique names or a registry of Minecraft recipes. */
public final class DescriptiveNameGenerator {
    public DrinkName generate(ProcessProfile process) {
        Objects.requireNonNull(process, "process");
        String dominant = process.ingredientRatios().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .findFirst().orElseThrow().getKey();
        String aging = process.aging().durationSeconds() > 0
                ? label(process.aging().woodType()) + " aged " : "";
        String style = process.distillation().passes() > 0 ? "spirit" : "brew";
        String magical = process.alchemicalAdditives().isEmpty() ? "" : " arcane";
        return new DrinkName(aging + label(dominant) + " " + label(process.preparationMethod())
                + magical + " " + style);
    }

    private String label(String id) {
        // Keep namespace text, so custom ingredient IDs remain intelligible.
        String text = id.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        if (text.isEmpty()) return "unnamed";
        int length = Math.min(18, text.codePointCount(0, text.length()));
        return text.substring(0, text.offsetByCodePoints(0, length)).strip();
    }
}
