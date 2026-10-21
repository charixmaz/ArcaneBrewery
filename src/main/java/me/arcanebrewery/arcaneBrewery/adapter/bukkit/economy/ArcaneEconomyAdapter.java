package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneeconomy.api.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Exact-money adapter. No Bukkit calls, implementation imports or cached ledger. */
public final class ArcaneEconomyAdapter implements EconomyPort {
    private final Supplier<ArcaneEconomyService> provider;
    private final BooleanSupplier blockingAllowed;

    public ArcaneEconomyAdapter(Supplier<ArcaneEconomyService> provider, BooleanSupplier blockingAllowed) {
        this.provider = Objects.requireNonNull(provider);
        this.blockingAllowed = Objects.requireNonNull(blockingAllowed);
    }

    public boolean canAfford(UUID player, BigDecimal amount) {
        if (player == null || amount == null || amount.signum() < 0 || !blockingAllowed.getAsBoolean()) return false;
        try {
            var service = provider.get();
            return compatible(service) && service.balance(player).compareTo(amount) >= 0;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    public boolean deposit(UUID player, BigDecimal amount, UUID transaction) {
        return mutate(player, amount, transaction, EconomyTransactionRecord.Type.DEPOSIT);
    }
    public boolean withdraw(UUID player, BigDecimal amount, UUID transaction) {
        return mutate(player, amount, transaction, EconomyTransactionRecord.Type.WITHDRAW);
    }

    private boolean mutate(UUID player, BigDecimal amount, UUID transaction, EconomyTransactionRecord.Type type) {
        if (player == null || amount == null || transaction == null || amount.signum() <= 0
                || !blockingAllowed.getAsBoolean()) return false;
        final ArcaneEconomyService service;
        try {
            service = provider.get();
            if (!compatible(service)) return false;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
        try {
            var result = type == EconomyTransactionRecord.Type.DEPOSIT
                    ? service.deposit(player, amount, transaction) : service.withdraw(player, amount, transaction);
            if (result == null || !transaction.equals(result.transactionId()))
                throw new IllegalStateException("invalid economy receipt");
            return switch (result.status()) {
                case APPLIED -> true;
                case DUPLICATE -> {
                    var record = service.transaction(transaction).orElseThrow();
                    if (record.type() != type || !record.transactionId().equals(transaction)
                            || !record.sourcePlayerId().equals(player) || record.targetPlayerId().isPresent()
                            || record.amount().compareTo(amount) != 0)
                        throw new IllegalStateException("conflicting economy receipt");
                    yield true;
                }
                case INVALID, OUT_OF_RANGE, INSUFFICIENT_FUNDS -> false;
            };
        } catch (RuntimeException | LinkageError uncertain) {
            // A provider can fail AFTER committing. Returning false here would
            // restore the sold item despite the player already receiving money.
            throw new EconomyOperationUncertainException(uncertain);
        }
    }

    private static boolean compatible(ArcaneEconomyService service) {
        return service != null && service.apiVersion() == ArcaneEconomyService.API_VERSION;
    }
}
