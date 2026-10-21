package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryMutationRuntimeTest {
    @Test
    void renamePersistsAndIncrementsRevision() {
        UUID owner = UUID.randomUUID();
        var repo = new FakeRepository(PlayerDiscovery.discover(owner, process(), Instant.EPOCH));
        var runtime = new DiscoveryMutationRuntime(repo, Runnable::run);

        var result = runtime.rename(owner, repo.stored.discovery().fingerprint(), "My Spirit").join();

        assertEquals("My Spirit", result.displayName().value());
        assertEquals(1, result.revision());
        assertTrue(repo.stored.discovery().renameUsed());
    }

    @Test
    void secondRenameStillFailsAfterReload() {
        UUID owner = UUID.randomUUID();
        var discovery = PlayerDiscovery.discover(owner, process(), Instant.EPOCH).rename(owner, "First Name");
        var repo = new FakeRepository(discovery);
        var runtime = new DiscoveryMutationRuntime(repo, Runnable::run);

        var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> runtime.rename(owner, discovery.fingerprint(), "Second Name").join());
        assertInstanceOf(IllegalStateException.class, root(failure));
    }

    @Test
    void nonOwnerCannotMutate() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        var discovery = PlayerDiscovery.discover(owner, process(), Instant.EPOCH);
        var repo = new FakeRepository(discovery);
        var runtime = new DiscoveryMutationRuntime(repo, Runnable::run);

        var failure = assertThrows(java.util.concurrent.CompletionException.class,
                () -> runtime.visibility(stranger, discovery.fingerprint(), DiscoveryVisibility.PUBLIC).join());
        assertInstanceOf(SecurityException.class, root(failure));
    }

    @Test
    void fullShareAndRevokePersist() {
        UUID owner = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        var discovery = PlayerDiscovery.discover(owner, process(), Instant.EPOCH);
        var repo = new FakeRepository(discovery);
        var runtime = new DiscoveryMutationRuntime(repo, Runnable::run);

        runtime.shareFull(owner, discovery.fingerprint(), recipient).join();
        assertEquals(RecipeKnowledge.State.FULL, repo.stored.discovery().knowledgeFor(recipient).state());

        runtime.revoke(owner, discovery.fingerprint(), recipient).join();
        assertEquals(RecipeKnowledge.State.UNKNOWN, repo.stored.discovery().knowledgeFor(recipient).state());
    }

    @Test
    void optimisticConflictRetriesAgainstFreshAggregate() {
        UUID owner = UUID.randomUUID();
        var discovery = PlayerDiscovery.discover(owner, process(), Instant.EPOCH);
        var repo = new FakeRepository(discovery);
        repo.failNextUpdate = true;
        var runtime = new DiscoveryMutationRuntime(repo, Runnable::run);

        var result = runtime.visibility(owner, discovery.fingerprint(), DiscoveryVisibility.PUBLIC).join();

        assertEquals(DiscoveryVisibility.PUBLIC, result.visibility());
        assertEquals(1, result.revision());
        assertEquals(2, repo.updateAttempts);
    }

    private static Throwable root(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current != current.getCause()) current = current.getCause();
        return current;
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
        StoredDiscovery stored;
        boolean failNextUpdate;
        int updateAttempts;

        FakeRepository(PlayerDiscovery discovery) {
            stored = new StoredDiscovery(discovery, 0);
        }

        public Optional<StoredDiscovery> findByFingerprint(DrinkFingerprint fingerprint) {
            return stored.discovery().fingerprint().equals(fingerprint) ? Optional.of(stored) : Optional.empty();
        }
        public List<StoredDiscovery> findOwnedBy(UUID owner, int limit, int offset) { return List.of(stored); }
        public boolean exists(DrinkFingerprint fingerprint) { return findByFingerprint(fingerprint).isPresent(); }
        public RegistrationResult insert(PlayerDiscovery discovery) { throw new UnsupportedOperationException(); }
        public StoredDiscovery update(long expectedRevision, PlayerDiscovery discovery) {
            updateAttempts++;
            if (failNextUpdate) {
                failNextUpdate = false;
                throw new OptimisticLockException();
            }
            if (expectedRevision != stored.revision()) throw new OptimisticLockException();
            stored = new StoredDiscovery(discovery, expectedRevision + 1);
            return stored;
        }
        public Optional<DiscoverySummary> findPublicSummary(DrinkFingerprint fingerprint) { return Optional.empty(); }
        public List<DiscoverySummary> findPublicSummaries(int limit, int offset) { return List.of(); }
        public RecipeKnowledge knowledgeFor(DrinkFingerprint fingerprint, UUID player) {
            return findByFingerprint(fingerprint)
                    .map(value -> value.discovery().knowledgeFor(player))
                    .orElseGet(() -> new RecipeKnowledge.Unknown(player));
        }
    }
}
