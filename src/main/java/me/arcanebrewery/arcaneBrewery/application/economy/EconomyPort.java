package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.UUID;

public interface EconomyPort {
    boolean canAfford(UUID playerId, double amount);
    boolean withdraw(UUID playerId, double amount, UUID transactionId);
    boolean deposit(UUID playerId, double amount, UUID transactionId);
}
