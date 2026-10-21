package me.arcanebrewery.arcaneeconomy.api;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EconomyTransactionRecord(
        UUID transactionId,
        Type type,
        UUID sourcePlayerId,
        Optional<UUID> targetPlayerId,
        BigDecimal amount,
        BigDecimal sourceBalanceAfter,
        Optional<BigDecimal> targetBalanceAfter,
        long createdAtEpochMillis
) {
    public enum Type { DEPOSIT, WITHDRAW, SET, TRANSFER }

    public EconomyTransactionRecord {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(sourcePlayerId);
        Objects.requireNonNull(targetPlayerId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(sourceBalanceAfter);
        Objects.requireNonNull(targetBalanceAfter);
        if (createdAtEpochMillis < 0) throw new IllegalArgumentException("createdAtEpochMillis must be non-negative");
    }
}
