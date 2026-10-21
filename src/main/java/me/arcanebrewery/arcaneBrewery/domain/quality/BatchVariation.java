package me.arcanebrewery.arcaneBrewery.domain.quality;

import java.util.SplittableRandom;

/** Immutable, bounded offsets; no RNG or mutable state is shared between batches. */
public record BatchVariation(double quality, double potency, double flavor) {
    public static final BatchVariation NONE = new BatchVariation(0, 0, 0);

    public BatchVariation {
        for (double offset : new double[]{quality, potency, flavor}) {
            if (!Double.isFinite(offset) || Math.abs(offset) > 2)
                throw new IllegalArgumentException("variation offsets must be within -2..2");
        }
    }

    public static BatchVariation fromSeed(long seed) {
        var random = new SplittableRandom(seed);
        return new BatchVariation(random.nextDouble(-2, 2), random.nextDouble(-2, 2),
                random.nextDouble(-2, 2));
    }
}
