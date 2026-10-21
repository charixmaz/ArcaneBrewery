package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneBrewery.application.storage.StorageException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class JdbcMarketEscrowStore implements MarketEscrowStore {
    private final JdbcConnectionFactory connections;

    public JdbcMarketEscrowStore(JdbcConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public boolean stage(UUID transactionId, UUID playerId, String assetToken, int inventorySlot, byte[] itemBytes) {
        Objects.requireNonNull(transactionId);
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(assetToken);
        Objects.requireNonNull(itemBytes);
        if (!assetToken.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid asset token");
        if (inventorySlot < 0 || inventorySlot > 40) throw new IllegalArgumentException("invalid inventory slot");
        if (itemBytes.length == 0 || itemBytes.length > 1_048_576) throw new IllegalArgumentException("invalid item payload");

        return JdbcTransactions.run(connections, true, connection -> {
            long now = System.currentTimeMillis();
            try (var insert = connection.prepareStatement("""
                    INSERT INTO market_escrow(
                        transaction_id, player_id, asset_token, inventory_slot,
                        state, item_payload, created_at, updated_at
                    ) VALUES(?, ?, ?, ?, 'STAGED', ?, ?, ?)
                    ON CONFLICT(transaction_id) DO NOTHING
                    """)) {
                insert.setString(1, transactionId.toString());
                insert.setString(2, playerId.toString());
                insert.setString(3, assetToken);
                insert.setInt(4, inventorySlot);
                insert.setBytes(5, itemBytes);
                insert.setLong(6, now);
                insert.setLong(7, now);
                return insert.executeUpdate() == 1;
            }
        });
    }

    @Override public void markRemoved(UUID id) { transition(id, Set.of(MarketEscrowEntry.State.STAGED), MarketEscrowEntry.State.REMOVED); }
    @Override public void markRestored(UUID id) { transition(id, Set.of(MarketEscrowEntry.State.REMOVED), MarketEscrowEntry.State.RESTORED); }
    @Override public void release(UUID id) { transition(id, Set.of(MarketEscrowEntry.State.REMOVED), MarketEscrowEntry.State.RELEASED); }
    @Override public void markReconcile(UUID id) {
        transition(id, Set.of(MarketEscrowEntry.State.STAGED, MarketEscrowEntry.State.REMOVED), MarketEscrowEntry.State.RECONCILE);
    }

    @Override
    public Optional<MarketEscrowEntry> find(UUID transactionId) {
        Objects.requireNonNull(transactionId);
        return JdbcTransactions.run(connections, false, connection -> {
            try (var select = connection.prepareStatement("SELECT * FROM market_escrow WHERE transaction_id=?")) {
                select.setString(1, transactionId.toString());
                try (var rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(read(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<MarketEscrowEntry> unresolved(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be within 1..1000");
        return JdbcTransactions.run(connections, false, connection -> {
            var entries = new ArrayList<MarketEscrowEntry>();
            try (var select = connection.prepareStatement("""
                    SELECT * FROM market_escrow
                    WHERE state IN ('STAGED','REMOVED','RECONCILE')
                    ORDER BY updated_at, transaction_id
                    LIMIT ?
                    """)) {
                select.setInt(1, limit);
                try (var rows = select.executeQuery()) {
                    while (rows.next()) entries.add(read(rows));
                }
            }
            return List.copyOf(entries);
        });
    }

    private void transition(UUID transactionId, Set<MarketEscrowEntry.State> allowed, MarketEscrowEntry.State desired) {
        Objects.requireNonNull(transactionId);
        JdbcTransactions.run(connections, true, connection -> {
            String placeholders = String.join(",", Collections.nCopies(allowed.size(), "?"));
            String sql = "UPDATE market_escrow SET state=?, updated_at=? WHERE transaction_id=? AND state IN (" + placeholders + ")";
            try (var update = connection.prepareStatement(sql)) {
                int index = 1;
                update.setString(index++, desired.name());
                update.setLong(index++, System.currentTimeMillis());
                update.setString(index++, transactionId.toString());
                for (var state : allowed) update.setString(index++, state.name());
                if (update.executeUpdate() == 1) return null;
            }

            try (var select = connection.prepareStatement("SELECT state FROM market_escrow WHERE transaction_id=?")) {
                select.setString(1, transactionId.toString());
                try (var rows = select.executeQuery()) {
                    if (!rows.next()) throw new StorageException("market escrow does not exist: " + transactionId);
                    var existing = MarketEscrowEntry.State.valueOf(rows.getString(1));
                    if (existing == desired) return null;
                    throw new StorageException("illegal market escrow transition " + existing + " -> " + desired);
                }
            }
        });
    }

    private static MarketEscrowEntry read(ResultSet rows) throws SQLException {
        return new MarketEscrowEntry(
                UUID.fromString(rows.getString("transaction_id")),
                UUID.fromString(rows.getString("player_id")),
                rows.getString("asset_token"),
                rows.getInt("inventory_slot"),
                MarketEscrowEntry.State.valueOf(rows.getString("state")),
                rows.getBytes("item_payload"),
                rows.getLong("created_at"),
                rows.getLong("updated_at")
        );
    }
}
