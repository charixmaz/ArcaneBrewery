package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Metadata safe for identification; deliberately contains no process or grants. */
public record DiscoverySummary(UUID discovererId, DrinkFingerprint fingerprint, Instant discoveredAt,
        DrinkName displayName, DiscoveryVisibility visibility) {
    public DiscoverySummary {
        Objects.requireNonNull(discovererId, "discovererId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(discoveredAt, "discoveredAt");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(visibility, "visibility");
    }
}
