package me.arcanebrewery.arcaneBrewery.domain.quality;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.Objects;

public record BatchSimulationResult(DrinkFingerprint fingerprint, BatchQuality quality,
        Contamination contamination) {
    public BatchSimulationResult {
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(quality);
        Objects.requireNonNull(contamination);
    }
}
