package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneBrewery.application.storage.StorageException;
import me.arcanebrewery.arcaneBrewery.storage.jdbc.JdbcTransactions;
import me.arcanebrewery.arcaneBrewery.storage.migration.MigrationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqliteMarketPersistenceTest {
    @TempDir Path directory;

    @Test
    void journalAndEscrowSurviveRestartAndResolveExplicitly() {
        Path file = directory.resolve("market.db");
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        var asset = asset();
        var quote = quote(asset);

        try (var provider = new SqliteStorageProvider(file)) {
            assertEquals(SqliteSchema.VERSION, provider.schemaVersion());
            var journal = provider.marketJournal();
            var escrow = provider.marketEscrow();

            assertTrue(journal.begin(new MarketTransaction(tx, player, quote, MarketTransaction.Status.PREPARED)));
            assertFalse(journal.begin(new MarketTransaction(tx, player, quote, MarketTransaction.Status.PREPARED)));

            assertTrue(escrow.stage(tx, player, asset.token(), 4, new byte[]{1, 2, 3}));
            assertFalse(escrow.stage(tx, player, asset.token(), 4, new byte[]{1, 2, 3}));
            escrow.markRemoved(tx);

            assertEquals(MarketTransaction.Status.PREPARED, journal.find(tx).orElseThrow().status());
            assertEquals(MarketEscrowEntry.State.REMOVED, escrow.find(tx).orElseThrow().state());
        }

        try (var reopened = new SqliteStorageProvider(file)) {
            assertEquals(1, reopened.marketJournal().prepared(10).size());
            assertEquals(1, reopened.marketEscrow().unresolved(10).size());

            reopened.marketEscrow().release(tx);
            reopened.marketJournal().commit(tx);

            assertEquals(MarketTransaction.Status.COMMITTED,
                    reopened.marketJournal().find(tx).orElseThrow().status());
            assertEquals(MarketEscrowEntry.State.RELEASED,
                    reopened.marketEscrow().find(tx).orElseThrow().state());
            assertTrue(reopened.marketJournal().prepared(10).isEmpty());
            assertTrue(reopened.marketEscrow().unresolved(10).isEmpty());
        }
    }

    @Test
    void rejectedTransactionCannotLaterCommit() {
        Path file = directory.resolve("reject.db");
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();

        try (var provider = new SqliteStorageProvider(file)) {
            var journal = provider.marketJournal();
            assertTrue(journal.begin(new MarketTransaction(
                    tx, player, quote(asset()), MarketTransaction.Status.PREPARED)));

            journal.reject(tx, "asset removal failed");
            journal.reject(tx, "asset removal failed");

            var entry = journal.find(tx).orElseThrow();
            assertEquals(MarketTransaction.Status.REJECTED, entry.status());
            assertEquals("asset removal failed", entry.reason());
            assertThrows(StorageException.class, () -> journal.commit(tx));
        }
    }

    @Test
    void stagedEscrowCanBeMarkedForManualReconciliation() {
        Path file = directory.resolve("reconcile.db");
        UUID player = UUID.randomUUID();
        UUID tx = UUID.randomUUID();
        var asset = asset();

        try (var provider = new SqliteStorageProvider(file)) {
            assertTrue(provider.marketJournal().begin(new MarketTransaction(
                    tx, player, quote(asset), MarketTransaction.Status.PREPARED)));
            assertTrue(provider.marketEscrow().stage(tx, player, asset.token(), 0, new byte[]{9}));
            provider.marketEscrow().markReconcile(tx);

            assertEquals(MarketEscrowEntry.State.RECONCILE,
                    provider.marketEscrow().find(tx).orElseThrow().state());
            assertEquals(1, provider.marketEscrow().unresolved(10).size());
        }
    }

    @Test
    void existingV1DatabaseMigratesToV2WithoutReplacingHistory() throws Exception {
        Path file = directory.resolve("upgrade.db");
        Class.forName("org.sqlite.JDBC");
        var factory = new SqliteConnectionFactory(file);

        new MigrationRunner(factory, java.util.List.of(SqliteSchema.migrations().getFirst())).migrate();
        assertEquals(1, JdbcTransactions.run(factory, false, MigrationRunner::currentVersion));

        try (var provider = new SqliteStorageProvider(file)) {
            assertEquals(SqliteSchema.VERSION, provider.schemaVersion());
            assertNotNull(provider.discoveries());
            assertNotNull(provider.marketJournal());
            assertNotNull(provider.marketEscrow());
        }
    }

    private static MarketAsset asset() {
        return MarketAsset.from("b".repeat(64), new DrinkValue(1, 100, 50, 30, 20, 10));
    }

    private static MarketQuote quote(MarketAsset asset) {
        return new MarketQuote(
                MarketQuote.VERSION,
                UUID.randomUUID(),
                asset.fingerprint(),
                asset.token(),
                MarketSide.PLAYER_SELLS,
                asset.value().units(),
                75.25,
                1_000,
                31_000
        );
    }
}
