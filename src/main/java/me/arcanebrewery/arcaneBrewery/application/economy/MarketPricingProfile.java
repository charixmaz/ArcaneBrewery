package me.arcanebrewery.arcaneBrewery.application.economy;

public record MarketPricingProfile(
        double sellMultiplier,
        double buyMultiplier,
        double minimumPrice,
        double maximumPrice,
        long quoteLifetimeMillis
) {
    public static final MarketPricingProfile DEFAULT =
            new MarketPricingProfile(0.75, 1.25, 0.01, 100_000.0, 30_000L);

    public MarketPricingProfile {
        if (!Double.isFinite(sellMultiplier) || sellMultiplier < 0)
            throw new IllegalArgumentException("sell multiplier must be finite and non-negative");
        if (!Double.isFinite(buyMultiplier) || buyMultiplier < 0)
            throw new IllegalArgumentException("buy multiplier must be finite and non-negative");
        if (buyMultiplier < sellMultiplier)
            throw new IllegalArgumentException("buy multiplier cannot be below sell multiplier");
        if (!Double.isFinite(minimumPrice) || minimumPrice < 0)
            throw new IllegalArgumentException("minimum price must be finite and non-negative");
        if (!Double.isFinite(maximumPrice) || maximumPrice < minimumPrice)
            throw new IllegalArgumentException("maximum price must be finite and >= minimum price");
        if (quoteLifetimeMillis <= 0)
            throw new IllegalArgumentException("quote lifetime must be positive");
    }
}
