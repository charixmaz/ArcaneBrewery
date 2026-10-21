package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SqliteMarketRestartStressTest {
    @TempDir Path temp;

    @Test
    void unresolvedMarketStateSurvivesFiftyReopensWithoutMutation() {
        Path file = temp.resolve("restart.db");
        UUID player = UUID.randomUUID();
        var expected = new LinkedHashMap<UUID, MarketEscrowEntry.State>();

        try (var provider = new SqliteStorageProvider(file)) {
            for (int i = 0; i < 100; i++) {
                UUID tx = UUID.nameUUIDFromBytes(("tx-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                MarketAsset asset = MarketAsset.from(hex(i), new DrinkValue(1, 100 + i, 40, 30, 20, 5));
                MarketQuote quote = new MarketQuote(
                        MarketQuote.VERSION, UUID.randomUUID(), asset.fingerprint(), asset.token(),
                        MarketSide.PLAYER_SELLS, asset.value().units(), 50 + i,
                        1_000L + i, 61_000L + i);

                assertTrue(provider.marketJournal().begin(
                        new MarketTransaction(tx, player, quote, MarketTransaction.Status.PREPARED)));
                assertTrue(provider.marketEscrow().stage(
                        tx, player, asset.token(), i % 41, new byte[]{1, 2, 3, (byte) i}));

                if (i % 3 == 0) {
                    provider.marketEscrow().markRemoved(tx);
                    expected.put(tx, MarketEscrowEntry.State.REMOVED);
                } else if (i % 3 == 1) {
                    provider.marketEscrow().markReconcile(tx);
                    expected.put(tx, MarketEscrowEntry.State.RECONCILE);
                } else {
                    expected.put(tx, MarketEscrowEntry.State.STAGED);
                }
            }
        }

        for (int reopen = 0; reopen < 50; reopen++) {
            try (var provider = new SqliteStorageProvider(file)) {
                assertEquals(100, provider.marketJournal().prepared(200).size());
                assertEquals(100, provider.marketEscrow().unresolved(200).size());
                for (var entry : provider.marketEscrow().unresolved(200)) {
                    assertEquals(expected.get(entry.transactionId()), entry.state());
                }
            }
        }
    }

    @Test
    void terminalRecordsStayTerminalAcrossRepeatedReopens() {
        Path file = temp.resolve("terminal.db");
        UUID player = UUID.randomUUID();

        for (int cycle = 0; cycle < 50; cycle++) {
            UUID tx = UUID.nameUUIDFromBytes(("terminal-" + cycle).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (var provider = new SqliteStorageProvider(file)) {
                MarketAsset asset = MarketAsset.from(hex(10_000 + cycle),
                        new DrinkValue(1, 200, 80, 60, 40, 0));
                MarketQuote quote = new MarketQuote(
                        MarketQuote.VERSION, UUID.randomUUID(), asset.fingerprint(), asset.token(),
                        MarketSide.PLAYER_SELLS, asset.value().units(), 150,
                        2_000L + cycle, 62_000L + cycle);

                assertTrue(provider.marketJournal().begin(
                        new MarketTransaction(tx, player, quote, MarketTransaction.Status.PREPARED)));
                assertTrue(provider.marketEscrow().stage(tx, player, asset.token(), 0, new byte[]{9, 8, 7}));
                provider.marketEscrow().markRemoved(tx);
                provider.marketEscrow().release(tx);
                provider.marketJournal().commit(tx);
            }

            try (var reopened = new SqliteStorageProvider(file)) {
                assertEquals(MarketTransaction.Status.COMMITTED,
                        reopened.marketJournal().find(tx).orElseThrow().status());
                assertEquals(MarketEscrowEntry.State.RELEASED,
                        reopened.marketEscrow().find(tx).orElseThrow().state());
                assertFalse(reopened.marketJournal().prepared(1000).stream()
                        .anyMatch(entry -> entry.transactionId().equals(tx)));
                assertFalse(reopened.marketEscrow().unresolved(1000).stream()
                        .anyMatch(entry -> entry.transactionId().equals(tx)));
            }
        }
    }

    private static String hex(int i) {
        return String.format("%064x", i + 1L);
    }
}
