package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.application.discovery.DiscoveryRegistrationService;
import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import me.arcanebrewery.arcaneBrewery.storage.StorageProviders;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static me.arcanebrewery.arcaneBrewery.storage.StorageFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SqliteDiscoveryRepositoryTest {
    @TempDir Path directory;
    private Path file;
    private SqliteStorageProvider provider;
    private DiscoveryRepository repository;

    @BeforeEach void open() {
        file = directory.resolve("nested/discoveries.db");
        provider = new SqliteStorageProvider(file);
        repository = provider.discoveries();
    }
    @AfterEach void close() { provider.close(); }

    private StoredDiscovery saved(PlayerDiscovery discovery) {
        assertEquals(RegistrationResult.Status.REGISTERED, repository.insert(discovery).status());
        return repository.findByFingerprint(discovery.fingerprint()).orElseThrow();
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (var connection = new SqliteConnectionFactory(file).open(true);
             var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setObject(i + 1, parameters[i]);
            statement.executeUpdate();
        }
    }

    @Test
    void discoveryRoundTripPreservesExactSnapshotAndNanosecondTimestamp() {
        var discovery = discovery();
        var saved = saved(discovery);
        assertEquals(0, saved.revision());
        assertEquals(discovery.snapshot(), saved.discovery().snapshot());
        assertEquals(TIME, saved.discovery().discoveredAt());
        assertEquals(RecipeKnowledge.State.UNKNOWN, repository.knowledgeFor(discovery.fingerprint(), FRIEND).state());
        assertTrue(repository.exists(discovery.fingerprint()));
    }

    @Test
    void renamedUnicodeDiscoveryRetainsOneTimeRename() {
        var discovery = discovery().rename(OWNER, "月光の秘酒 🍷 Žitná");
        var restored = saved(discovery).discovery();
        assertEquals(discovery.snapshot(), restored.snapshot());
        assertTrue(restored.renameUsed());
        assertThrows(IllegalStateException.class, () -> restored.rename(OWNER, "Again"));
    }

    @Test
    void fullAndPartialGrantsRoundTripWithoutBroadeningKnowledge() {
        var discovery = discovery().shareFull(OWNER, FRIEND)
                .sharePartial(OWNER, OTHER, Set.of(ProcessField.AGING_WOOD, ProcessField.FERMENTATION_TEMPERATURE));
        var restored = saved(discovery).discovery();
        assertEquals(discovery.snapshot(), restored.snapshot());
        assertEquals(discovery.knowledgeFor(FRIEND), restored.knowledgeFor(FRIEND));
        assertEquals(discovery.knowledgeFor(OTHER), restored.knowledgeFor(OTHER));
        var partial = assertInstanceOf(RecipeKnowledge.Partial.class, restored.knowledgeFor(OTHER));
        assertEquals(Set.of(ProcessField.AGING_WOOD, ProcessField.FERMENTATION_TEMPERATURE),
                partial.disclosure().fields().keySet());
    }

    @Test
    void allVisibilityStatesRoundTripAndPublicQueriesContainOnlyMetadata() {
        var privateDiscovery = discovery();
        saved(privateDiscovery);
        assertTrue(repository.findPublicSummary(privateDiscovery.fingerprint()).isEmpty());
        var shared = repository.update(0, privateDiscovery.shareFull(OWNER, FRIEND));
        assertEquals(DiscoveryVisibility.SHARED, repository.findByFingerprint(privateDiscovery.fingerprint())
                .orElseThrow().discovery().visibility());
        var publicDiscovery = shared.discovery().changeVisibility(OWNER, DiscoveryVisibility.PUBLIC);
        repository.update(1, publicDiscovery);
        var summary = repository.findPublicSummary(privateDiscovery.fingerprint()).orElseThrow();
        assertEquals(OWNER, summary.discovererId());
        assertEquals(DiscoveryVisibility.PUBLIC, summary.visibility());
        assertEquals(List.of(summary), repository.findPublicSummaries(20, 0));
        assertEquals(RecipeKnowledge.State.IDENTIFIED, repository.knowledgeFor(privateDiscovery.fingerprint(), OTHER).state());
        assertTrue(repository.findPublicSummaries(20, 1).isEmpty());
    }

    @Test
    void revisionsIncrementAndSecondStaleRenameIsRejected() {
        var original = saved(discovery());
        var renamed = repository.update(original.revision(), original.discovery().rename(OWNER, "First"));
        assertEquals(1, renamed.revision());
        assertThrows(OptimisticLockException.class, () -> repository.update(original.revision(),
                original.discovery().rename(OWNER, "Second")));
        assertEquals("First", repository.findByFingerprint(original.discovery().fingerprint())
                .orElseThrow().discovery().displayName().value());
    }

    @Test
    void staleSnapshotsCannotRestoreRevokedGrantsOrOverwriteVisibility() {
        var original = saved(discovery().shareFull(OWNER, FRIEND));
        var revoked = repository.update(0, original.discovery().revoke(OWNER, FRIEND));
        assertEquals(1, revoked.revision());
        assertThrows(OptimisticLockException.class, () -> repository.update(0,
                original.discovery().changeVisibility(OWNER, DiscoveryVisibility.PUBLIC)));
        assertEquals(RecipeKnowledge.State.UNKNOWN, repository.knowledgeFor(original.discovery().fingerprint(), FRIEND).state());
        assertEquals(DiscoveryVisibility.PRIVATE, repository.findByFingerprint(original.discovery().fingerprint())
                .orElseThrow().discovery().visibility());
    }

    @Test
    void evenFreshRevisionCannotResetRenameOrReplaceOriginalOwner() {
        var original = saved(discovery());
        repository.update(0, original.discovery().rename(OWNER, "Permanent"));
        assertThrows(OptimisticLockException.class, () -> repository.update(1, original.discovery()));
        var otherOwner = PlayerDiscovery.discover(OTHER, process(), TIME).rename(OTHER, "Permanent");
        assertThrows(OptimisticLockException.class, () -> repository.update(1, otherOwner));
        assertEquals(OWNER, repository.findByFingerprint(original.discovery().fingerprint()).orElseThrow().discovery().discovererId());
    }

    @Test
    void duplicateFingerprintCannotCreateSecondOwner() {
        var original = saved(discovery());
        var result = repository.insert(PlayerDiscovery.discover(OTHER, process(), TIME.plusSeconds(1)));
        assertEquals(RegistrationResult.Status.ALREADY_DISCOVERED, result.status());
        assertEquals(OWNER, result.originalDiscoverer());
        assertEquals(original.discovery().snapshot(), repository.findByFingerprint(result.fingerprint()).orElseThrow().discovery().snapshot());
    }

    @Test
    void updatesCannotReplaceOriginalProcessTimestampOrTemporaryName() {
        var original = saved(discovery());
        var differentConditions = PlayerDiscovery.discover(OWNER,
                withFermentation(fermentation(22, 3600, 50, 0)), TIME);
        assertEquals(original.discovery().fingerprint(), differentConditions.fingerprint());
        assertThrows(OptimisticLockException.class, () -> repository.update(0, differentConditions));
        assertThrows(OptimisticLockException.class, () -> repository.update(0,
                PlayerDiscovery.discover(OWNER, process(), TIME.plusSeconds(1))));
        var s = original.discovery().snapshot();
        var changedName = PlayerDiscovery.restore(new DiscoverySnapshot(OWNER, s.fingerprint(), s.process(),
                TIME, new DrinkName("Changed Temporary Name"), s.canonicalName(), s.visibility(), s.grants()));
        assertThrows(OptimisticLockException.class, () -> repository.update(0, changedName));
        assertEquals(s, repository.findByFingerprint(s.fingerprint()).orElseThrow().discovery().snapshot());
    }

    @Test
    void simultaneousRegistrationThroughIndependentProvidersHasExactlyOneWinner() throws Exception {
        try (var second = new SqliteStorageProvider(file); var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<RegistrationResult> first = () -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return new DiscoveryRegistrationService(repository).register(OWNER, process(), TIME);
            };
            Callable<RegistrationResult> other = () -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return new DiscoveryRegistrationService(second.discoveries()).register(OTHER, process(), TIME);
            };
            var a = pool.submit(first); var b = pool.submit(other);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            var results = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(r -> r.status() == RegistrationResult.Status.REGISTERED).count());
            assertEquals(1, results.stream().filter(r -> r.status() == RegistrationResult.Status.ALREADY_DISCOVERED).count());
            assertEquals(results.get(0).originalDiscoverer(), results.get(1).originalDiscoverer());
            assertEquals(results.get(0).originalDiscoverer(),
                    repository.findByFingerprint(discovery().fingerprint()).orElseThrow().discovery().discovererId());
        }
    }

    @Test
    void simultaneousUpdatesHaveOneWinner() throws Exception {
        var original = saved(discovery());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> first = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try { repository.update(0, original.discovery().rename(OWNER, "First")); return true; }
                catch (OptimisticLockException expected) { return false; }
            };
            Callable<Boolean> second = () -> {
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try { repository.update(0, original.discovery().rename(OWNER, "Second")); return true; }
                catch (OptimisticLockException expected) { return false; }
            };
            var a = pool.submit(first); var b = pool.submit(second); start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, repository.findByFingerprint(original.discovery().fingerprint()).orElseThrow().revision());
        }
    }

    @Test
    void ownerAndFingerprintLookupsAreExactAndPaginated() {
        var first = saved(discovery());
        var changed = withFermentation(fermentation(23, 3600, 100, 0));
        var second = saved(PlayerDiscovery.discover(OWNER, changed, TIME.plusSeconds(1)));
        assertEquals(2, repository.findOwnedBy(OWNER, 20, 0).size());
        assertEquals(1, repository.findOwnedBy(OWNER, 1, 1).size());
        assertTrue(repository.findOwnedBy(OTHER, 20, 0).isEmpty());
        assertEquals(first.discovery().snapshot(), repository.findByFingerprint(first.discovery().fingerprint()).orElseThrow().discovery().snapshot());
        assertTrue(repository.exists(second.discovery().fingerprint()));
        var unknown = me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint.from(
                withFermentation(fermentation(99, 3600, 100, 0)));
        assertFalse(repository.exists(unknown));
        assertTrue(repository.findByFingerprint(unknown).isEmpty());
        assertEquals(new RecipeKnowledge.Unknown(OTHER), repository.knowledgeFor(unknown, OTHER));
    }

    @Test
    void reopeningRepositoryRetainsDataAndClosedRepositoryCannotBeUsed() {
        var original = saved(discovery().rename(OWNER, "Persistent").shareFull(OWNER, FRIEND));
        provider.close();
        assertThrows(StorageException.class, () -> repository.findByFingerprint(original.discovery().fingerprint()));
        provider = new SqliteStorageProvider(file);
        assertEquals(original.discovery().snapshot(), provider.discoveries().findByFingerprint(
                original.discovery().fingerprint()).orElseThrow().discovery().snapshot());
    }

    @Test
    void malformedProcessPayloadFailsSafelyWithoutDiscardingRecord() throws SQLException {
        var original = saved(discovery());
        execute("UPDATE discoveries SET process_payload = ? WHERE fingerprint = ?", new byte[]{1, 2, 3},
                original.discovery().fingerprint().value());
        assertThrows(CorruptRecordException.class, () -> repository.findByFingerprint(original.discovery().fingerprint()));
        assertTrue(repository.exists(original.discovery().fingerprint()));
    }

    @Test
    void corruptPartialGrantFailsInsteadOfBecomingFullOrUnknown() throws SQLException {
        var original = saved(discovery().sharePartial(OWNER, FRIEND, Set.of(ProcessField.AGING_WOOD)));
        execute("UPDATE recipe_grants SET selection_payload = ? WHERE fingerprint = ?",
                "1:UNKNOWN_FIELD", original.discovery().fingerprint().value());
        assertThrows(CorruptRecordException.class, () -> repository.findByFingerprint(original.discovery().fingerprint()));
    }

    @Test
    void persistedProcessMustMatchAuthoritativeFingerprint() throws SQLException {
        var original = saved(discovery());
        var changed = withFermentation(fermentation(23, 3600, 100, 0));
        execute("UPDATE discoveries SET process_payload = ? WHERE fingerprint = ?",
                new me.arcanebrewery.arcaneBrewery.storage.codec.ProcessProfileCodec().encode(changed),
                original.discovery().fingerprint().value());
        assertThrows(CorruptRecordException.class, () -> repository.findByFingerprint(original.discovery().fingerprint()));
    }

    @Test
    void sqlInjectionLikeNamesAndCustomIngredientIdsRemainData() {
        var p = process();
        var custom = new ProcessProfile(Map.of("mod:grain'); drop table discoveries;--", 1.0),
                "mash", p.fermentation(), p.distillation(), p.aging(), p.alchemicalAdditives());
        var discovery = PlayerDiscovery.discover(OWNER, custom, TIME).rename(OWNER, "Moon'); DROP TABLE discoveries; --");
        assertEquals(discovery.snapshot(), saved(discovery).discovery().snapshot());
        assertEquals(1, repository.findOwnedBy(OWNER, 20, 0).size());
        assertEquals(2, provider.schemaVersion());
    }

    @Test
    void failedGrantWriteRollsBackRenameRevisionVisibilityAndGrantReplacement() throws SQLException {
        var original = saved(discovery().shareFull(OWNER, FRIEND));
        execute("CREATE TRIGGER reject_grants BEFORE INSERT ON recipe_grants BEGIN SELECT RAISE(ABORT, 'blocked'); END");
        var update = original.discovery().rename(OWNER, "Must Roll Back")
                .changeVisibility(OWNER, DiscoveryVisibility.PUBLIC)
                .sharePartial(OWNER, OTHER, Set.of(ProcessField.AGING_WOOD));
        assertThrows(StorageException.class, () -> repository.update(0, update));
        var restored = repository.findByFingerprint(original.discovery().fingerprint()).orElseThrow();
        assertEquals(0, restored.revision());
        assertEquals(original.discovery().snapshot(), restored.discovery().snapshot());
    }

    @Test
    void failedNewGrantWriteRollsBackEntireRegistration() throws SQLException {
        execute("CREATE TRIGGER reject_grants BEFORE INSERT ON recipe_grants BEGIN SELECT RAISE(ABORT, 'blocked'); END");
        var discovery = discovery().shareFull(OWNER, FRIEND);
        assertThrows(StorageException.class, () -> repository.insert(discovery));
        assertFalse(repository.exists(discovery.fingerprint()));
    }

    @Test
    void publicSummaryQueriesDoNotReadSecretPayload() throws SQLException {
        var original = saved(discovery().changeVisibility(OWNER, DiscoveryVisibility.PUBLIC));
        execute("UPDATE discoveries SET process_payload = ? WHERE fingerprint = ?",
                new byte[]{1}, original.discovery().fingerprint().value());
        assertEquals(OWNER, repository.findPublicSummary(original.discovery().fingerprint()).orElseThrow().discovererId());
        assertEquals(1, repository.findPublicSummaries(20, 0).size());
        assertThrows(CorruptRecordException.class, () -> repository.findByFingerprint(original.discovery().fingerprint()));
    }

    @Test
    void foreignKeysAreEnabledForEveryConnectionAndOrphanGrantsAreRejected() throws SQLException {
        try (var connection = new SqliteConnectionFactory(file).open(true);
             var statement = connection.prepareStatement("PRAGMA foreign_keys"); var rows = statement.executeQuery()) {
            assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
        }
        assertThrows(SQLException.class, () -> execute(
                "INSERT INTO recipe_grants(fingerprint, recipient_id, kind) VALUES (?, ?, 'FULL')",
                "a".repeat(64), FRIEND.toString()));
    }

    @Test
    void defaultProviderCreatesInjectedDirectoryAndExpectedDatabaseFile() {
        try (var storage = StorageProviders.openDefault(directory.resolve("plugin-data"))) {
            assertEquals(2, storage.schemaVersion());
            assertTrue(java.nio.file.Files.exists(directory.resolve("plugin-data/arcanebrewery.db")));
        }
    }

    @Test
    void invalidPaginationAndRevisionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> repository.findOwnedBy(OWNER, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.findPublicSummaries(201, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.findPublicSummaries(1, -1));
        assertThrows(IllegalArgumentException.class, () -> repository.update(-1, discovery()));
        assertThrows(IllegalArgumentException.class, () -> repository.update(Long.MAX_VALUE, discovery()));
        assertThrows(OptimisticLockException.class, () -> repository.update(0, discovery()));
    }
}
