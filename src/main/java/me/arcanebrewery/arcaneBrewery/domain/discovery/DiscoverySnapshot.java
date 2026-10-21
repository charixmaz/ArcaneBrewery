package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Trusted reconstitution state; contains secrets and is never a player-facing projection. */
public record DiscoverySnapshot(UUID discovererId, DrinkFingerprint fingerprint, ProcessProfile process,
        Instant discoveredAt, DrinkName temporaryName, Optional<DrinkName> canonicalName,
        DiscoveryVisibility visibility, Map<UUID, RecipeGrant> grants) {
    public DiscoverySnapshot {
        Objects.requireNonNull(discovererId);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(process);
        Objects.requireNonNull(discoveredAt);
        Objects.requireNonNull(temporaryName);
        Objects.requireNonNull(canonicalName);
        Objects.requireNonNull(visibility);
        grants = Map.copyOf(grants);
        if (!DrinkFingerprint.from(process).equals(fingerprint))
            throw new IllegalArgumentException("process does not match discovery identity");
        if (grants.containsKey(discovererId))
            throw new IllegalArgumentException("owner cannot be a recipient");
        if (visibility == DiscoveryVisibility.PRIVATE && !grants.isEmpty()
                || visibility == DiscoveryVisibility.SHARED && grants.isEmpty())
            throw new IllegalArgumentException("visibility does not match grants");
    }
}
