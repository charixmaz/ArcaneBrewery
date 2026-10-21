package me.arcanebrewery.arcaneBrewery.application.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketQuoteServiceTest {
    private static final String FP = "a".repeat(64);
    private final DrinkValue value = new DrinkValue(1, 100, 50, 30, 20, 10);

    @Test
    void sellAndBuyUseConfiguredSpread() {
        var service = new MarketQuoteService(MarketPricingProfile.DEFAULT);
        var asset = MarketAsset.from(FP, value);

        var sell = service.quote(asset, MarketSide.PLAYER_SELLS, 1_000, UUID.randomUUID());
        var buy = service.quote(asset, MarketSide.PLAYER_BUYS, 1_000, UUID.randomUUID());

        assertEquals(75.0, sell.price());
        assertEquals(125.0, buy.price());
        assertTrue(buy.price() > sell.price());
    }

    @Test
    void quoteIsBoundToExactAssetAndExpires() {
        var service = new MarketQuoteService(MarketPricingProfile.DEFAULT);
        var asset = MarketAsset.from(FP, value);
        var quote = service.quote(asset, MarketSide.PLAYER_SELLS, 1_000, UUID.randomUUID());

        assertTrue(quote.matches(asset));
        assertFalse(quote.expired(30_999));
        assertTrue(quote.expired(31_000));

        var modified = MarketAsset.from(FP, new DrinkValue(1, 101, 50, 30, 20, 10));
        assertFalse(quote.matches(modified));
    }

    @Test
    void configuredPriceCapsAreApplied() {
        var pricing = new MarketPricingProfile(0.5, 2.0, 50, 120, 10_000);
        var service = new MarketQuoteService(pricing);

        assertEquals(50, service.quote(
                MarketAsset.from(FP, new DrinkValue(1, 10, 1, 1, 1, 1)),
                MarketSide.PLAYER_SELLS, 0, UUID.randomUUID()).price());

        assertEquals(120, service.quote(
                MarketAsset.from(FP, new DrinkValue(1, 100, 1, 1, 1, 1)),
                MarketSide.PLAYER_BUYS, 0, UUID.randomUUID()).price());
    }

    @Test
    void invalidSpreadOrLifetimeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarketPricingProfile(2.0, 1.0, 0, 100, 30_000));
        assertThrows(IllegalArgumentException.class,
                () -> new MarketPricingProfile(0.5, 1.0, 0, 100, 0));
    }
}
