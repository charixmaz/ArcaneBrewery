package me.arcanebrewery.arcaneBrewery.application.storage;

import me.arcanebrewery.arcaneBrewery.application.economy.MarketEscrowStore;
import me.arcanebrewery.arcaneBrewery.application.economy.RecoverableMarketTransactionJournal;

public interface StorageProvider extends AutoCloseable {
    DiscoveryRepository discoveries();
    default me.arcanebrewery.arcaneBrewery.application.discovery.MaterialDiscoveryRepository materials() {throw new UnsupportedOperationException("material discovery storage unavailable");}
    default DiscoveryRepository gameplayDiscoveries() { return discoveries(); }
    RecoverableMarketTransactionJournal marketJournal();
    MarketEscrowStore marketEscrow();
    int schemaVersion();
    @Override void close();
}
