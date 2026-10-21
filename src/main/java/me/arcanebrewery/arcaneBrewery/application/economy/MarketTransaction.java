package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.Objects;
import java.util.UUID;

public record MarketTransaction(
        UUID transactionId,
        UUID playerId,
        MarketQuote quote,
        Status status
) {
    public enum Status { PREPARED, COMMITTED, REJECTED }

    public MarketTransaction {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(quote);
        Objects.requireNonNull(status);
    }

    public MarketTransaction commit() {
        if (status == Status.COMMITTED) return this;
        if (status != Status.PREPARED) throw new IllegalStateException("transaction is not prepared");
        return new MarketTransaction(transactionId, playerId, quote, Status.COMMITTED);
    }

    public MarketTransaction reject() {
        if (status == Status.REJECTED) return this;
        if (status != Status.PREPARED) throw new IllegalStateException("transaction is not prepared");
        return new MarketTransaction(transactionId, playerId, quote, Status.REJECTED);
    }
}
