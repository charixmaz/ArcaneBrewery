package me.arcanebrewery.arcaneBrewery.application.storage;

import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Aggregate reads are trusted application operations; use projections for player-facing queries. */
public interface DiscoveryRepository {
    Optional<StoredDiscovery> findByFingerprint(DrinkFingerprint fingerprint);
    List<StoredDiscovery> findOwnedBy(UUID owner, int limit, int offset);
    boolean exists(DrinkFingerprint fingerprint);
    RegistrationResult insert(PlayerDiscovery discovery);
    StoredDiscovery update(long expectedRevision, PlayerDiscovery discovery);
    Optional<DiscoverySummary> findPublicSummary(DrinkFingerprint fingerprint);
    List<DiscoverySummary> findPublicSummaries(int limit, int offset);
    RecipeKnowledge knowledgeFor(DrinkFingerprint fingerprint, UUID player);
    // Deliberately no deletion API or ownership-transfer operation.
}
