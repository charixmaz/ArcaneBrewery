package me.arcanebrewery.arcaneeconomy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record EconomyResult(
        UUID transactionId,
        Status status,
        BigDecimal balance,
        String message
) {
    public enum Status {
        APPLIED,
        DUPLICATE,
        INSUFFICIENT_FUNDS,
        OUT_OF_RANGE,
        INVALID
    }

    public EconomyResult {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(balance);
        message = message == null ? "" : message;
    }

    public boolean applied() {
        return status == Status.APPLIED;
    }
}
