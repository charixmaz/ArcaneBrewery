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

class DiscoveryRuntimeTest {
    @Test
    void registrationRunsThroughInjectedExecutorAndReturnsResult() {
        UUID player = UUID.randomUUID();
        ProcessProfile process = process();
        var repository = new FakeRepository();
        var runtime = new DiscoveryRuntime(new DiscoveryRegistrationService(repository), Runnable::run);

        RegistrationResult result = runtime.register(player, process, Instant.EPOCH).join();

        assertEquals(RegistrationResult.Status.REGISTERED, result.status());
        assertEquals(player, result.originalDiscoverer());
        assertEquals(DrinkFingerprint.from(process), result.fingerprint());
        assertNotNull(repository.inserted);
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
        PlayerDiscovery inserted;
        public RegistrationResult insert(PlayerDiscovery discovery) {
            inserted = discovery;
            return new RegistrationResult(RegistrationResult.Status.REGISTERED,
                    discovery.fingerprint(), discovery.discovererId());
        }
        public Optional<StoredDiscovery> findByFingerprint(DrinkFingerprint fingerprint) { return Optional.empty(); }
        public List<StoredDiscovery> findOwnedBy(UUID owner, int limit, int offset) { return List.of(); }
        public boolean exists(DrinkFingerprint fingerprint) { return false; }
        public StoredDiscovery update(long expectedRevision, PlayerDiscovery discovery) { throw new UnsupportedOperationException(); }
        public Optional<DiscoverySummary> findPublicSummary(DrinkFingerprint fingerprint) { return Optional.empty(); }
        public List<DiscoverySummary> findPublicSummaries(int limit, int offset) { return List.of(); }
        public RecipeKnowledge knowledgeFor(DrinkFingerprint fingerprint, UUID player) { return new RecipeKnowledge.Unknown(player); }
    }
}
