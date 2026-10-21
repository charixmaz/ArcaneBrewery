package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneBrewery.application.storage.StorageException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class JdbcMarketTransactionJournal implements RecoverableMarketTransactionJournal {
    private final JdbcConnectionFactory connections;

    public JdbcMarketTransactionJournal(JdbcConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public boolean begin(MarketTransaction transaction) {
        Objects.requireNonNull(transaction);
        if (transaction.status() != MarketTransaction.Status.PREPARED) {
            throw new IllegalArgumentException("new market transaction must be PREPARED");
        }
        return JdbcTransactions.run(connections, true, connection -> {
            long now = System.currentTimeMillis();
            MarketQuote quote = transaction.quote();
            try (var insert = connection.prepareStatement("""
                    INSERT INTO market_transactions(
                        transaction_id, player_id, quote_version, quote_id,
                        fingerprint, asset_token, side, intrinsic_value, price,
                        quote_created_at, quote_expires_at, status, reason,
                        created_at, updated_at
                    ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PREPARED', '', ?, ?)
                    ON CONFLICT(transaction_id) DO NOTHING
                    """)) {
                insert.setString(1, transaction.transactionId().toString());
                insert.setString(2, transaction.playerId().toString());
                insert.setInt(3, quote.version());
                insert.setString(4, quote.quoteId().toString());
                insert.setString(5, quote.fingerprint());
                insert.setString(6, quote.assetToken());
                insert.setString(7, quote.side().name());
                insert.setString(8, Double.toString(quote.intrinsicValueUnits()));
                insert.setString(9, Double.toString(quote.price()));
                insert.setLong(10, quote.createdAtEpochMillis());
                insert.setLong(11, quote.expiresAtEpochMillis());
                insert.setLong(12, now);
                insert.setLong(13, now);
                return insert.executeUpdate() == 1;
            }
        });
    }

    @Override
    public void commit(UUID transactionId) {
        transition(transactionId, MarketTransaction.Status.COMMITTED, "");
    }

    @Override
    public void reject(UUID transactionId, String reason) {
        transition(transactionId, MarketTransaction.Status.REJECTED, boundedReason(reason));
    }

    @Override
    public Optional<MarketJournalEntry> find(UUID transactionId) {
        Objects.requireNonNull(transactionId);
        return JdbcTransactions.run(connections, false, connection -> {
            try (var select = connection.prepareStatement("""
                    SELECT * FROM market_transactions WHERE transaction_id=?
                    """)) {
                select.setString(1, transactionId.toString());
                try (var rows = select.executeQuery()) {
                    return rows.next() ? Optional.of(read(rows)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<MarketJournalEntry> prepared(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("limit must be within 1..1000");
        return JdbcTransactions.run(connections, false, connection -> {
            var entries = new ArrayList<MarketJournalEntry>();
            try (var select = connection.prepareStatement("""
                    SELECT * FROM market_transactions
                    WHERE status='PREPARED'
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

    private void transition(UUID transactionId, MarketTransaction.Status desired, String reason) {
        Objects.requireNonNull(transactionId);
        if (desired == MarketTransaction.Status.PREPARED) {
            throw new IllegalArgumentException("cannot transition back to PREPARED");
        }
        JdbcTransactions.run(connections, true, connection -> {
            try (var update = connection.prepareStatement("""
                    UPDATE market_transactions
                    SET status=?, reason=?, updated_at=?
                    WHERE transaction_id=? AND status='PREPARED'
                    """)) {
                update.setString(1, desired.name());
                update.setString(2, reason);
                update.setLong(3, System.currentTimeMillis());
                update.setString(4, transactionId.toString());
                if (update.executeUpdate() == 1) return null;
            }

            try (var select = connection.prepareStatement(
                    "SELECT status FROM market_transactions WHERE transaction_id=?")) {
                select.setString(1, transactionId.toString());
                try (var rows = select.executeQuery()) {
                    if (!rows.next()) throw new StorageException("market transaction does not exist: " + transactionId);
                    MarketTransaction.Status existing = MarketTransaction.Status.valueOf(rows.getString(1));
                    if (existing == desired) return null;
                    throw new StorageException("illegal market transaction transition " + existing + " -> " + desired);
                }
            }
        });
    }

    private static MarketJournalEntry read(ResultSet rows) throws SQLException {
        var quote = new MarketQuote(
                rows.getInt("quote_version"),
                UUID.fromString(rows.getString("quote_id")),
                rows.getString("fingerprint"),
                rows.getString("asset_token"),
                MarketSide.valueOf(rows.getString("side")),
                Double.parseDouble(rows.getString("intrinsic_value")),
                Double.parseDouble(rows.getString("price")),
                rows.getLong("quote_created_at"),
                rows.getLong("quote_expires_at")
        );
        return new MarketJournalEntry(
                UUID.fromString(rows.getString("transaction_id")),
                UUID.fromString(rows.getString("player_id")),
                quote,
                MarketTransaction.Status.valueOf(rows.getString("status")),
                rows.getString("reason"),
                rows.getLong("created_at"),
                rows.getLong("updated_at")
        );
    }

    private static String boundedReason(String reason) {
        String value = reason == null ? "" : reason.strip();
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }
}
