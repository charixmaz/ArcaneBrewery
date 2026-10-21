package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.UUID;

public interface MarketTransactionJournal {
    boolean begin(MarketTransaction transaction);
    void commit(UUID transactionId);
    void reject(UUID transactionId, String reason);
}
