package me.arcanebrewery.arcaneBrewery.domain.drink;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

public record DrinkFingerprint(String value) {
    /**
     * Versioned, length-prefixed encoding prevents delimiter collisions and numeric rounding aliases.
     * Sanitation, oxygen, oxidation, cleanliness and storage are batch conditions, not drink identity.
     */
    public static DrinkFingerprint from(ProcessProfile process) {
        Objects.requireNonNull(process, "process");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(1);
            amounts(out, process.ingredientRatios());
            out.writeUTF(process.preparationMethod());
            var fermentation = process.fermentation();
            out.writeUTF(fermentation.yeastOrCatalyst());
            out.writeDouble(fermentation.temperatureCelsius());
            out.writeLong(fermentation.durationSeconds());
            var distillation = process.distillation();
            out.writeDouble(distillation.headsCutPercent());
            out.writeDouble(distillation.heartsCutPercent());
            out.writeDouble(distillation.tailsCutPercent());
            out.writeDouble(distillation.targetTemperatureCelsius());
            out.writeInt(distillation.passes());
            out.writeUTF(process.aging().woodType());
            out.writeLong(process.aging().durationSeconds());
            amounts(out, process.alchemicalAdditives());
            out.flush();
            return new DrinkFingerprint(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to encode fingerprint", e);
        }
    }

    private static void amounts(DataOutputStream out, Map<String, Double> amounts) throws IOException {
        out.writeInt(amounts.size());
        for (var entry : amounts.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeDouble(entry.getValue());
        }
    }
}
