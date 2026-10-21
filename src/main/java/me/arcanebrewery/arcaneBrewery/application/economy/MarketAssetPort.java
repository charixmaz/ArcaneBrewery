package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.UUID;

public interface MarketAssetPort {
    boolean removeForSale(UUID playerId, MarketAsset asset, UUID transactionId);
    boolean restoreAfterFailedSale(UUID playerId, MarketAsset asset, UUID transactionId);
}
