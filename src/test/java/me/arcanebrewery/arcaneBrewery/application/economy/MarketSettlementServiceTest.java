package me.arcanebrewery.arcaneBrewery.application.economy;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketSettlementServiceTest {
    private static final String FP = "b".repeat(64);
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void successfulSaleRemovesAssetThenDepositsOnce() {
        var journal = new Journal();
        var assets = new Assets();
        var economy = new Economy();
        var service = new MarketSettlementService(journal, assets, economy);
        var asset = asset();
        var quote = quote(asset, 1_000, 31_000);
        UUID tx = UUID.randomUUID();

        var result = service.settleSale(PLAYER, asset, quote, tx, 2_000);

        assertEquals(MarketSettlementResult.Status.COMMITTED, result.status());
        assertEquals(75.0, result.amount());
        assertEquals(List.of("remove", "deposit", "commit"), journaledOrder(journal, assets, economy));
        assertEquals(1, economy.deposits);
        assertEquals(0, assets.restores);
    }

    @Test
    void duplicateTransactionNeverTouchesAssetOrEconomy() {
        var journal = new Journal();
        journal.seen.add(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var assets = new Assets();
        var economy = new Economy();
        var service = new MarketSettlementService(journal, assets, economy);
        UUID tx = UUID.fromString("00000000-0000-0000-0000-000000000001");

        var result = service.settleSale(PLAYER, asset(), quote(asset(), 1_000, 31_000), tx, 2_000);

        assertEquals(MarketSettlementResult.Status.DUPLICATE, result.status());
        assertEquals(0, assets.removes);
        assertEquals(0, economy.deposits);
    }

    @Test
    void expiredOrMismatchedQuoteIsRejectedBeforeJournalReservation() {
        var journal = new Journal();
        var assets = new Assets();
        var economy = new Economy();
        var service = new MarketSettlementService(journal, assets, economy);
        var asset = asset();

        var expired = service.settleSale(
                PLAYER, asset, quote(asset, 1_000, 2_000), UUID.randomUUID(), 2_000);
        assertEquals(MarketSettlementResult.Status.REJECTED, expired.status());
        assertEquals(0, journal.begins);

        var other = MarketAsset.from("c".repeat(64), asset.value());
        var mismatch = service.settleSale(
                PLAYER, other, quote(asset, 1_000, 31_000), UUID.randomUUID(), 2_000);
        assertEquals(MarketSettlementResult.Status.REJECTED, mismatch.status());
        assertEquals(0, journal.begins);
    }

    @Test
    void failedDepositRestoresAssetAndRejectsTransaction() {
        var journal = new Journal();
        var assets = new Assets();
        var economy = new Economy();
        economy.depositSucceeds = false;
        var service = new MarketSettlementService(journal, assets, economy);
        UUID tx = UUID.randomUUID();

        var result = service.settleSale(
                PLAYER, asset(), quote(asset(), 1_000, 31_000), tx, 2_000);

        assertEquals(MarketSettlementResult.Status.REJECTED, result.status());
        assertEquals(1, assets.removes);
        assertEquals(1, assets.restores);
        assertEquals(1, journal.rejects);
        assertEquals(0, journal.commits);
    }

    @Test
    void failedAssetRemovalNeverDeposits() {
        var journal = new Journal();
        var assets = new Assets();
        assets.removeSucceeds = false;
        var economy = new Economy();
        var service = new MarketSettlementService(journal, assets, economy);

        var result = service.settleSale(
                PLAYER, asset(), quote(asset(), 1_000, 31_000), UUID.randomUUID(), 2_000);

        assertEquals(MarketSettlementResult.Status.REJECTED, result.status());
        assertEquals(0, economy.deposits);
        assertEquals(1, journal.rejects);
    }

    private static MarketAsset asset() {
        return MarketAsset.from(FP, new DrinkValue(1, 100, 50, 30, 20, 10));
    }

    private static MarketQuote quote(MarketAsset asset, long created, long expires) {
        return new MarketQuote(
                1, UUID.randomUUID(), asset.fingerprint(), asset.token(),
                MarketSide.PLAYER_SELLS, asset.value().units(), 75,
                created, expires);
    }

    private static List<String> journaledOrder(Journal journal, Assets assets, Economy economy) {
        var events = new ArrayList<String>();
        events.addAll(assets.events);
        events.addAll(economy.events);
        events.addAll(journal.events);
        events.sort(Comparator.comparingInt(MarketSettlementServiceTest::rank));
        return events;
    }

    private static int rank(String value) {
        return switch (value) {
            case "remove" -> 0;
            case "deposit" -> 1;
            case "commit" -> 2;
            default -> 3;
        };
    }

    private static final class Journal implements MarketTransactionJournal {
        final Set<UUID> seen = new HashSet<>();
        final List<String> events = new ArrayList<>();
        int begins;
        int commits;
        int rejects;

        public boolean begin(MarketTransaction transaction) {
            begins++;
            if (!seen.add(transaction.transactionId())) return false;
            return true;
        }

        public void commit(UUID transactionId) {
            commits++;
            events.add("commit");
        }

        public void reject(UUID transactionId, String reason) {
            rejects++;
        }
    }

    private static final class Assets implements MarketAssetPort {
        final List<String> events = new ArrayList<>();
        boolean removeSucceeds = true;
        boolean restoreSucceeds = true;
        int removes;
        int restores;

        public boolean removeForSale(UUID playerId, MarketAsset asset, UUID transactionId) {
            removes++;
            if (removeSucceeds) events.add("remove");
            return removeSucceeds;
        }

        public boolean restoreAfterFailedSale(UUID playerId, MarketAsset asset, UUID transactionId) {
            restores++;
            return restoreSucceeds;
        }
    }

    private static final class Economy implements EconomyPort {
        final List<String> events = new ArrayList<>();
        boolean depositSucceeds = true;
        int deposits;

        public boolean canAfford(UUID playerId, double amount) { return true; }
        public boolean withdraw(UUID playerId, double amount, UUID transactionId) { return true; }

        public boolean deposit(UUID playerId, double amount, UUID transactionId) {
            deposits++;
            if (depositSucceeds) events.add("deposit");
            return depositSucceeds;
        }
    }
}
