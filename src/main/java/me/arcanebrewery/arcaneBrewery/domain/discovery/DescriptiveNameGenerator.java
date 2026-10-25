package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;

/** Broad descriptions, not unique names or a registry of Minecraft recipes. */
public final class DescriptiveNameGenerator {
    public DrinkName generate(ProcessProfile process) {
        Objects.requireNonNull(process, "process");
        String dominant = process.ingredientRatios().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .findFirst().orElseThrow().getKey();
        String aging = process.aging().durationSeconds() > 0
                ? label(process.aging().woodType()) + "-aged " : "";
        String magical = process.alchemicalAdditives().isEmpty() ? "" : " Arcane";
        return new DrinkName(aging + label(dominant) + magical + " Elixir");
    }

    private String label(String id) {
        // Keep namespace text, so custom ingredient IDs remain intelligible.
        if (id.startsWith("minecraft:")) id = id.substring(10);
        String text = id.replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        if (text.isEmpty()) return "unnamed";
        int length = Math.min(18, text.codePointCount(0, text.length()));
        text = text.substring(0, text.offsetByCodePoints(0, length)).strip();
        StringBuilder titled = new StringBuilder();
        for (String word : text.split(" +")) {
            if (!titled.isEmpty()) titled.append(' ');
            int first = word.offsetByCodePoints(0, 1);
            titled.append(word.substring(0, first).toUpperCase(Locale.ROOT)).append(word.substring(first));
        }
        return titled.toString();
    }
}
