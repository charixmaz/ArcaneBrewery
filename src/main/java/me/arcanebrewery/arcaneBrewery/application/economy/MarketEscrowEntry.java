package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record MarketEscrowEntry(
        UUID transactionId,
        UUID playerId,
        String assetToken,
        int inventorySlot,
        State state,
        byte[] itemBytes,
        long createdAtEpochMillis,
        long updatedAtEpochMillis
) {
    public enum State { STAGED, REMOVED, RESTORED, RELEASED, RECONCILE }

    public MarketEscrowEntry {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(playerId);
        assetToken = Objects.requireNonNull(assetToken).strip();
        Objects.requireNonNull(state);
        itemBytes = Arrays.copyOf(Objects.requireNonNull(itemBytes), itemBytes.length);
        if (!assetToken.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid asset token");
        if (inventorySlot < 0 || inventorySlot > 40) throw new IllegalArgumentException("invalid inventory slot");
        if (itemBytes.length == 0 || itemBytes.length > 1_048_576) throw new IllegalArgumentException("invalid escrow item payload");
        if (createdAtEpochMillis < 0 || updatedAtEpochMillis < createdAtEpochMillis) {
            throw new IllegalArgumentException("invalid escrow timestamps");
        }
    }

    @Override public byte[] itemBytes() { return Arrays.copyOf(itemBytes, itemBytes.length); }
}
