package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.domain.discovery.DiscoveryVisibility;
import me.arcanebrewery.arcaneBrewery.domain.discovery.DrinkName;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;

import java.util.Objects;

public record DiscoveryMutationResult(
        DrinkFingerprint fingerprint,
        DrinkName displayName,
        DiscoveryVisibility visibility,
        long revision
) {
    public DiscoveryMutationResult {
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(visibility);
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
    }
}
