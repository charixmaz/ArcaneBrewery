package me.arcanebrewery.arcaneBrewery.application.economy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record MarketAsset(String fingerprint, DrinkValue value, String token) {
    public MarketAsset {
        fingerprint = Objects.requireNonNull(fingerprint).strip();
        Objects.requireNonNull(value);
        token = Objects.requireNonNull(token).strip();
        if (!fingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("fingerprint must be lowercase SHA-256 hex");
        if (!token.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("asset token must be lowercase SHA-256 hex");
    }

    public static MarketAsset from(String fingerprint, DrinkValue value) {
        Objects.requireNonNull(value);
        String normalized = Objects.requireNonNull(fingerprint).strip();
        String payload = normalized + "|" + value.version() + "|" + value.units()
                + "|" + value.qualityComponent() + "|" + value.processComponent()
                + "|" + value.alchemyComponent() + "|" + value.contaminationPenalty();
        return new MarketAsset(normalized, value, sha256(payload));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
