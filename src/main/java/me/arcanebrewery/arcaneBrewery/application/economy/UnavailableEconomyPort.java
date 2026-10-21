package me.arcanebrewery.arcaneBrewery.application.economy;

import java.math.BigDecimal;
import java.util.UUID;

/** API-free fallback; absence never disables brewing gameplay. */
public enum UnavailableEconomyPort implements EconomyPort {
    INSTANCE;
    public boolean canAfford(UUID player, BigDecimal amount) { return false; }
    public boolean withdraw(UUID player, BigDecimal amount, UUID transaction) { return false; }
    public boolean deposit(UUID player, BigDecimal amount, UUID transaction) { return false; }
}
