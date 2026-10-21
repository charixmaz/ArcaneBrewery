package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Owner-safe listing projection; contains no process or recipient grants. */
public record OwnedDiscoveryView(
        UUID ownerId,
        DrinkFingerprint fingerprint,
        DrinkName displayName,
        Instant discoveredAt,
        DiscoveryVisibility visibility,
        boolean renamed
) {
    public OwnedDiscoveryView {
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(discoveredAt);
        Objects.requireNonNull(visibility);
    }

    public static OwnedDiscoveryView from(PlayerDiscovery discovery) {
        Objects.requireNonNull(discovery);
        return new OwnedDiscoveryView(
                discovery.discovererId(),
                discovery.fingerprint(),
                discovery.displayName(),
                discovery.discoveredAt(),
                discovery.visibility(),
                discovery.renameUsed()
        );
    }
}
