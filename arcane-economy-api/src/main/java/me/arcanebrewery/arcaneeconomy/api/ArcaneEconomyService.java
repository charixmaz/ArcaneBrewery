package me.arcanebrewery.arcaneeconomy.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Synchronous, potentially blocking ledger API. Call off the server thread.
 * Amounts are exact decimals; unsupported precision is rejected, never rounded.
 * Persist a unique transaction ID before calling and reuse it only for the same
 * operation, account(s) and amount. DUPLICATE confirms that exact request applied;
 * a conflicting request returns INVALID. Storage failures may throw and require
 * reconciliation with the same ID, never a fresh retry ID.
 */
public interface ArcaneEconomyService extends TransactionLookup {
    int API_VERSION = 1;

    default int apiVersion() { return API_VERSION; }

    BigDecimal balance(UUID playerId);

    EconomyResult deposit(UUID playerId, BigDecimal amount, UUID transactionId);

    EconomyResult withdraw(UUID playerId, BigDecimal amount, UUID transactionId);

    EconomyResult setBalance(UUID playerId, BigDecimal balance, UUID transactionId);

    EconomyResult transfer(UUID fromPlayerId, UUID toPlayerId, BigDecimal amount, UUID transactionId);
}
