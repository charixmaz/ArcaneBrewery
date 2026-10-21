package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.Objects;
import java.util.UUID;

public final class MarketQuoteService {
    private final MarketPricingProfile pricing;

    public MarketQuoteService(MarketPricingProfile pricing) {
        this.pricing = Objects.requireNonNull(pricing);
    }

    public MarketQuote quote(String fingerprint, DrinkValue value, MarketSide side,
            long nowEpochMillis, UUID quoteId) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(side);
        double multiplier = side == MarketSide.PLAYER_SELLS
                ? pricing.sellMultiplier() : pricing.buyMultiplier();
        double price = clamp(value.units() * multiplier);
        return new MarketQuote(
                MarketQuote.VERSION,
                quoteId,
                fingerprint,
                side,
                value.units(),
                round2(price),
                nowEpochMillis
        );
    }

    private double clamp(double value) {
        return Math.max(pricing.minimumPrice(), Math.min(pricing.maximumPrice(), value));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
