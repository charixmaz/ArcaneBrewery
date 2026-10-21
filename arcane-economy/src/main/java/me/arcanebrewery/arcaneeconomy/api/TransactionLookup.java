package me.arcanebrewery.arcaneeconomy.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionLookup {
    Optional<EconomyTransactionRecord> transaction(UUID transactionId);

    List<EconomyTransactionRecord> history(UUID playerId, int limit);
}
