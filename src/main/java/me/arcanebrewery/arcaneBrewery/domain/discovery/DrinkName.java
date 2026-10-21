package me.arcanebrewery.arcaneBrewery.domain.discovery;

import java.util.Objects;

/** Presentation only. Identity must always use DrinkFingerprint. */
public record DrinkName(String value) {
    public static final int MAX_LENGTH = 80;

    public DrinkName {
        Objects.requireNonNull(value, "name");
        if (value.codePoints().anyMatch(DrinkName::forbidden))
            throw new IllegalArgumentException("name contains control or invisible formatting characters");
        value = value.replaceAll("(?U)\\s+", " ").strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > MAX_LENGTH)
            throw new IllegalArgumentException("name must contain 1.." + MAX_LENGTH + " characters");
    }

    private static boolean forbidden(int codePoint) {
        int type = Character.getType(codePoint);
        return Character.isISOControl(codePoint) || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                || type == Character.SURROGATE;
    }
}
