package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.PlayerDiscovery;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Database uniqueness arbitrates ownership; there is deliberately no check-then-insert race. */
public final class DiscoveryRegistrationService {
    private final DiscoveryRepository repository;
    public DiscoveryRegistrationService(DiscoveryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }
    public RegistrationResult register(UUID player, ProcessProfile process, Instant completedAt) {
        return repository.insert(PlayerDiscovery.discover(player, process, completedAt));
    }
}
