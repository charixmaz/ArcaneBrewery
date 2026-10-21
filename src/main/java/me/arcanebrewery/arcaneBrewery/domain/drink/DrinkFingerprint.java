package me.arcanebrewery.arcaneBrewery.domain.drink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record DrinkFingerprint(String value) {

    public static DrinkFingerprint from(
            Map<String, Double> ingredientRatios,
            String preparationMethod,
            String yeastOrCatalyst,
            double fermentationTemperature,
            long fermentationSeconds,
            String distillationCuts,
            String agingWood,
            long agingSeconds,
            String alchemicalAdditives
    ) {
        TreeMap<String, Double> sorted = new TreeMap<>(ingredientRatios);
        StringBuilder canonical = new StringBuilder();

        sorted.forEach((id, ratio) -> canonical
                .append(id.toLowerCase(Locale.ROOT))
                .append('=')
                .append(String.format(Locale.ROOT, "%.6f", ratio))
                .append(';'));

        canonical.append('|').append(normalize(preparationMethod))
                .append('|').append(normalize(yeastOrCatalyst))
                .append('|').append(String.format(Locale.ROOT, "%.2f", fermentationTemperature))
                .append('|').append(fermentationSeconds)
                .append('|').append(normalize(distillationCuts))
                .append('|').append(normalize(agingWood))
                .append('|').append(agingSeconds)
                .append('|').append(normalize(alchemicalAdditives));

        return new DrinkFingerprint(sha256(canonical.toString()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
