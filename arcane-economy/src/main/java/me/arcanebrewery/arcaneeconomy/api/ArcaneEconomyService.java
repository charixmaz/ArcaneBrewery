package me.arcanebrewery.arcaneeconomy.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface ArcaneEconomyService extends TransactionLookup {
    BigDecimal balance(UUID playerId);

    EconomyResult deposit(UUID playerId, BigDecimal amount, UUID transactionId);

    EconomyResult withdraw(UUID playerId, BigDecimal amount, UUID transactionId);

    EconomyResult setBalance(UUID playerId, BigDecimal balance, UUID transactionId);

    EconomyResult transfer(UUID fromPlayerId, UUID toPlayerId, BigDecimal amount, UUID transactionId);
}
