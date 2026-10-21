package me.arcanebrewery.arcaneBrewery.application.storage;

import me.arcanebrewery.arcaneBrewery.domain.discovery.PlayerDiscovery;
import java.util.Objects;

/** Trusted aggregate plus persisted optimistic-lock token, not a player-facing DTO. */
public record StoredDiscovery(PlayerDiscovery discovery, long revision) {
    public StoredDiscovery {
        Objects.requireNonNull(discovery);
        if (revision < 0) throw new IllegalArgumentException("revision must be non-negative");
    }
}
