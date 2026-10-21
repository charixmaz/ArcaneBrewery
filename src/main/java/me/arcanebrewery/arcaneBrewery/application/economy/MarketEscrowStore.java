package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketEscrowStore {
    boolean stage(UUID transactionId, UUID playerId, String assetToken, int inventorySlot, byte[] itemBytes);
    void markRemoved(UUID transactionId);
    void markRestored(UUID transactionId);
    void release(UUID transactionId);
    void markReconcile(UUID transactionId);
    Optional<MarketEscrowEntry> find(UUID transactionId);
    List<MarketEscrowEntry> unresolved(int limit);
}
