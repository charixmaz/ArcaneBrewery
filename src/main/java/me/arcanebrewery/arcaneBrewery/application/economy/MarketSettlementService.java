package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.Objects;
import java.util.UUID;

public final class MarketSettlementService {
    private final MarketTransactionJournal journal;
    private final MarketAssetPort assets;
    private final EconomyPort economy;

    public MarketSettlementService(
            MarketTransactionJournal journal,
            MarketAssetPort assets,
            EconomyPort economy) {
        this.journal = Objects.requireNonNull(journal);
        this.assets = Objects.requireNonNull(assets);
        this.economy = Objects.requireNonNull(economy);
    }

    public MarketSettlementResult settleSale(
            UUID playerId, MarketAsset asset, MarketQuote quote, UUID transactionId) {
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(quote);
        Objects.requireNonNull(transactionId);

        if (quote.side() != MarketSide.PLAYER_SELLS || !quote.matches(asset)) {
            return new MarketSettlementResult(
                    transactionId, MarketSettlementResult.Status.REJECTED, 0, "quote does not match asset");
        }

        var transaction = new MarketTransaction(
                transactionId, playerId, quote, MarketTransaction.Status.PREPARED);
        if (!journal.begin(transaction)) {
            return new MarketSettlementResult(
                    transactionId, MarketSettlementResult.Status.DUPLICATE, 0, "transaction already exists");
        }

        if (!assets.removeForSale(playerId, asset, transactionId)) {
            journal.reject(transactionId, "asset removal failed");
            return new MarketSettlementResult(
                    transactionId, MarketSettlementResult.Status.REJECTED, 0, "asset removal failed");
        }

        if (!economy.deposit(playerId, quote.price(), transactionId)) {
            boolean restored = assets.restoreAfterFailedSale(playerId, asset, transactionId);
            journal.reject(transactionId, restored
                    ? "economy deposit failed; asset restored"
                    : "economy deposit failed; asset restore requires reconciliation");
            return new MarketSettlementResult(
                    transactionId, MarketSettlementResult.Status.REJECTED, 0,
                    restored ? "economy deposit failed" : "manual reconciliation required");
        }

        journal.commit(transactionId);
        return new MarketSettlementResult(
                transactionId, MarketSettlementResult.Status.COMMITTED, quote.price(), "");
    }
}
