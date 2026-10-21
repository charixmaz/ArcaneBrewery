package me.arcanebrewery.arcaneBrewery.domain.process;

import java.util.Locale;
import java.util.Objects;

/** Shared validation for finite domain quantities and custom ingredient identifiers. */
public final class DomainValues {
    private DomainValues() {}

    public static String id(String value) {
        String normalized = Objects.requireNonNull(value, "id")
                .replaceAll("(?U)\\s+", " ").strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("id must not be blank");
        return normalized;
    }

    public static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        return value == 0 ? 0 : value;
    }

    public static double percent(double value) {
        return Math.max(0, Math.min(100, finite(value)));
    }

    public static double nonNegative(double value) {
        finite(value);
        if (value < 0) throw new IllegalArgumentException("value must be non-negative");
        return value == 0 ? 0 : value;
    }
}
