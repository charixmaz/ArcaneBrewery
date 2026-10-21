package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.Objects;
import java.util.UUID;

public record MarketJournalEntry(
        UUID transactionId,
        UUID playerId,
        MarketQuote quote,
        MarketTransaction.Status status,
        String reason,
        long createdAtEpochMillis,
        long updatedAtEpochMillis
) {
    public MarketJournalEntry {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(quote);
        Objects.requireNonNull(status);
        reason = reason == null ? "" : reason;
        if (createdAtEpochMillis < 0 || updatedAtEpochMillis < createdAtEpochMillis) {
            throw new IllegalArgumentException("invalid journal timestamps");
        }
    }
}
