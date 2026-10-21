package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecoverableMarketTransactionJournal extends MarketTransactionJournal {
    Optional<MarketJournalEntry> find(UUID transactionId);
    List<MarketJournalEntry> prepared(int limit);
}
