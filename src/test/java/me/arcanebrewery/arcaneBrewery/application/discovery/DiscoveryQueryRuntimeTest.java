package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryQueryRuntimeTest {
    @Test
    void ownedResultsAreProjectedBeforeReturning() {
        UUID owner = UUID.randomUUID();
        var discovery = PlayerDiscovery.discover(owner, process(), Instant.EPOCH);
        var repository = new FakeRepository(new StoredDiscovery(discovery, 0));
        var runtime = new DiscoveryQueryRuntime(repository, Runnable::run);

        var views = runtime.owned(owner, 8, 0).join();

        assertEquals(1, views.size());
        assertEquals(discovery.fingerprint(), views.getFirst().fingerprint());
        assertEquals(discovery.displayName(), views.getFirst().displayName());
    }

    @Test
    void knowledgeUsesRecipientProjection() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        var discovery = PlayerDiscovery.discover(owner, process(), Instant.EPOCH);
        var repository = new FakeRepository(new StoredDiscovery(discovery, 0));
        var runtime = new DiscoveryQueryRuntime(repository, Runnable::run);

        assertEquals(RecipeKnowledge.State.UNKNOWN,
                runtime.knowledge(stranger, discovery.fingerprint()).join().state());
        assertEquals(RecipeKnowledge.State.FULL,
                runtime.knowledge(owner, discovery.fingerprint()).join().state());
    }

    private static ProcessProfile process() {
        return new ProcessProfile(
                Map.of("minecraft:wheat", 1.0),
                "distillation",
                new FermentationProfile("arcanebrewery:wild_yeast", 22, 60, 85, 10),
                new DistillationProfile(10, 80, 10, 78, 1),
                new AgingProfile("oak", 120, 12, 18, 95),
                Map.of()
        );
    }

    private static final class FakeRepository implements DiscoveryRepository {
        private final StoredDiscovery stored;
        FakeRepository(StoredDiscovery stored) { this.stored = stored; }

        public Optional<StoredDiscovery> findByFingerprint(DrinkFingerprint fingerprint) {
            return stored.discovery().fingerprint().equals(fingerprint) ? Optional.of(stored) : Optional.empty();
        }
        public List<StoredDiscovery> findOwnedBy(UUID owner, int limit, int offset) {
            return stored.discovery().discovererId().equals(owner) && offset == 0 ? List.of(stored) : List.of();
        }
        public boolean exists(DrinkFingerprint fingerprint) { return findByFingerprint(fingerprint).isPresent(); }
        public RegistrationResult insert(PlayerDiscovery discovery) { throw new UnsupportedOperationException(); }
        public StoredDiscovery update(long expectedRevision, PlayerDiscovery discovery) { throw new UnsupportedOperationException(); }
        public Optional<DiscoverySummary> findPublicSummary(DrinkFingerprint fingerprint) { return Optional.empty(); }
        public List<DiscoverySummary> findPublicSummaries(int limit, int offset) { return List.of(); }
        public RecipeKnowledge knowledgeFor(DrinkFingerprint fingerprint, UUID player) {
            return findByFingerprint(fingerprint)
                    .map(value -> value.discovery().knowledgeFor(player))
                    .orElseGet(() -> new RecipeKnowledge.Unknown(player));
        }
    }
}
