package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.UUID;
import java.math.BigDecimal;

public interface EconomyPort {
    boolean canAfford(UUID playerId, BigDecimal amount);
    boolean withdraw(UUID playerId, BigDecimal amount, UUID transactionId);
    boolean deposit(UUID playerId, BigDecimal amount, UUID transactionId);
}
