package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.PlayerDiscovery;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persistence arbitrates personal production and server-first recognition atomically. */
public final class DiscoveryRegistrationService {
    private final DiscoveryRepository repository;
    public DiscoveryRegistrationService(DiscoveryRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }
    public RegistrationResult register(UUID player,ProcessProfile process,Instant at,double quality,String batchKey,byte[] evidence) {
        return repository.recordProduction(player,process,at,quality,batchKey,evidence);
    }
    public RegistrationResult register(UUID player, ProcessProfile process, Instant completedAt, double quality) {
        return repository.recordProduction(player,process,completedAt,quality,UUID.randomUUID().toString());
    }
    public RegistrationResult register(UUID player, ProcessProfile process, Instant completedAt) {
        return repository.insert(PlayerDiscovery.discover(player, process, completedAt));
    }
}
