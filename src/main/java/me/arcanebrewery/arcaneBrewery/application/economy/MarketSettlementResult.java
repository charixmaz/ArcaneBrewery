package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.Objects;
import java.util.UUID;

public record MarketSettlementResult(
        UUID transactionId,
        Status status,
        double amount,
        String reason
) {
    public enum Status { COMMITTED, REJECTED, DUPLICATE }

    public MarketSettlementResult {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(status);
        if (!Double.isFinite(amount) || amount < 0)
            throw new IllegalArgumentException("amount must be finite and non-negative");
        reason = reason == null ? "" : reason;
    }
}
