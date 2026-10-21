package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.Objects;
import java.util.UUID;

public record MarketQuote(
        int version,
        UUID quoteId,
        String fingerprint,
        MarketSide side,
        double intrinsicValueUnits,
        double price,
        long createdAtEpochMillis
) {
    public static final int VERSION = 1;

    public MarketQuote {
        if (version != VERSION) throw new IllegalArgumentException("unsupported market quote version");
        Objects.requireNonNull(quoteId);
        fingerprint = Objects.requireNonNull(fingerprint).strip();
        if (fingerprint.isEmpty()) throw new IllegalArgumentException("fingerprint is blank");
        Objects.requireNonNull(side);
        if (!Double.isFinite(intrinsicValueUnits) || intrinsicValueUnits < 0)
            throw new IllegalArgumentException("intrinsic value must be finite and non-negative");
        if (!Double.isFinite(price) || price < 0)
            throw new IllegalArgumentException("price must be finite and non-negative");
        if (createdAtEpochMillis < 0)
            throw new IllegalArgumentException("quote timestamp must be non-negative");
    }
}
