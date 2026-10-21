package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.storage.codec.ProcessProfileCodec;
import java.sql.*;
import java.util.*;

/** Portable bound-parameter JDBC DML. Aggregate and grant rows share one transaction. */
public final class JdbcDiscoveryRepository implements DiscoveryRepository {
    private final JdbcConnectionFactory connections;
    private final DiscoveryRows rows = new DiscoveryRows();
    private final ProcessProfileCodec processes = new ProcessProfileCodec();

    public JdbcDiscoveryRepository(JdbcConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    public Optional<StoredDiscovery> findByFingerprint(DrinkFingerprint fingerprint) {
        Objects.requireNonNull(fingerprint);
        return JdbcTransactions.run(connections, false, connection -> rows.load(connection, fingerprint));
    }

    public List<StoredDiscovery> findOwnedBy(UUID owner, int limit, int offset) {
        Objects.requireNonNull(owner); page(limit, offset);
        return JdbcTransactions.run(connections, false, connection -> {
            var fingerprints = new ArrayList<DrinkFingerprint>();
            try (var statement = connection.prepareStatement(
                    "SELECT fingerprint FROM discoveries WHERE owner_id = ? ORDER BY fingerprint LIMIT ? OFFSET ?")) {
                statement.setString(1, owner.toString()); statement.setInt(2, limit); statement.setInt(3, offset);
                try (var result = statement.executeQuery()) {
                    while (result.next()) fingerprints.add(new DrinkFingerprint(result.getString(1)));
                }
            }
            var discoveries = new ArrayList<StoredDiscovery>();
            for (var fingerprint : fingerprints) discoveries.add(rows.load(connection, fingerprint).orElseThrow(
                    () -> new CorruptRecordException("Discovery disappeared within a read snapshot")));
            return List.copyOf(discoveries);
        });
    }

    public boolean exists(DrinkFingerprint fingerprint) {
        Objects.requireNonNull(fingerprint);
        return JdbcTransactions.run(connections, false, connection -> {
            try (var statement = connection.prepareStatement("SELECT 1 FROM discoveries WHERE fingerprint = ?")) {
                statement.setString(1, fingerprint.value());
                try (var result = statement.executeQuery()) { return result.next(); }
            }
        });
    }

    public RegistrationResult insert(PlayerDiscovery discovery) {
        DiscoverySnapshot snapshot = Objects.requireNonNull(discovery).snapshot();
        byte[] payload = processes.encode(snapshot.process());
        try {
            return JdbcTransactions.run(connections, true, connection -> {
                try (var statement = connection.prepareStatement(
                        "INSERT INTO discoveries(fingerprint, owner_id, process_payload, discovered_at, "
                                + "temporary_name, canonical_name, visibility, revision) VALUES (?, ?, ?, ?, ?, ?, ?, 0)")) {
                    statement.setString(1, snapshot.fingerprint().value());
                    statement.setString(2, snapshot.discovererId().toString());
                    statement.setBytes(3, payload);
                    statement.setString(4, snapshot.discoveredAt().toString());
                    statement.setString(5, snapshot.temporaryName().value());
                    statement.setString(6, snapshot.canonicalName().map(DrinkName::value).orElse(null));
                    statement.setString(7, snapshot.visibility().name());
                    statement.executeUpdate();
                }
                rows.writeGrants(connection, snapshot);
                return new RegistrationResult(RegistrationResult.Status.REGISTERED, snapshot.fingerprint(), snapshot.discovererId());
            });
        } catch (StorageException failure) {
            if (!constraintViolation(failure)) throw failure;
            // A failed insert has rolled back. Read the winner on a fresh connection/transaction.
            return JdbcTransactions.run(connections, false, connection -> {
                try (var statement = connection.prepareStatement("SELECT owner_id FROM discoveries WHERE fingerprint = ?")) {
                    statement.setString(1, snapshot.fingerprint().value());
                    try (var result = statement.executeQuery()) {
                        if (!result.next()) throw failure; // Not a fingerprint conflict: do not hide other constraints.
                        return new RegistrationResult(RegistrationResult.Status.ALREADY_DISCOVERED, snapshot.fingerprint(),
                                DiscoveryRows.uuid(result.getString(1)));
                    }
                }
            });
        }
    }

    public StoredDiscovery update(long expectedRevision, PlayerDiscovery discovery) {
        if (expectedRevision < 0 || expectedRevision == Long.MAX_VALUE)
            throw new IllegalArgumentException("invalid expected revision");
        DiscoverySnapshot snapshot = Objects.requireNonNull(discovery).snapshot();
        byte[] payload = processes.encode(snapshot.process());
        return JdbcTransactions.run(connections, true, connection -> {
            try (var statement = connection.prepareStatement(
                    "UPDATE discoveries SET canonical_name = ?, visibility = ?, revision = revision + 1 "
                            + "WHERE fingerprint = ? AND revision = ? AND owner_id = ? AND process_payload = ? "
                            + "AND discovered_at = ? AND temporary_name = ? AND (canonical_name IS NULL OR canonical_name = ?)")) {
                String name = snapshot.canonicalName().map(DrinkName::value).orElse(null);
                statement.setString(1, name); statement.setString(2, snapshot.visibility().name());
                statement.setString(3, snapshot.fingerprint().value()); statement.setLong(4, expectedRevision);
                statement.setString(5, snapshot.discovererId().toString()); statement.setBytes(6, payload);
                statement.setString(7, snapshot.discoveredAt().toString()); statement.setString(8, snapshot.temporaryName().value());
                statement.setString(9, name);
                if (statement.executeUpdate() != 1) throw new OptimisticLockException();
            }
            try (var statement = connection.prepareStatement("DELETE FROM recipe_grants WHERE fingerprint = ?")) {
                statement.setString(1, snapshot.fingerprint().value()); statement.executeUpdate();
            }
            rows.writeGrants(connection, snapshot);
            return new StoredDiscovery(discovery, expectedRevision + 1);
        });
    }

    public Optional<DiscoverySummary> findPublicSummary(DrinkFingerprint fingerprint) {
        Objects.requireNonNull(fingerprint);
        return JdbcTransactions.run(connections, false, connection -> {
            try (var statement = connection.prepareStatement("SELECT " + DiscoveryRows.SUMMARY_COLUMNS
                    + " FROM discoveries WHERE fingerprint = ? AND visibility = 'PUBLIC'")) {
                statement.setString(1, fingerprint.value());
                try (var result = statement.executeQuery()) {
                    return result.next() ? Optional.of(DiscoveryRows.summary(result)) : Optional.empty();
                }
            }
        });
    }

    public List<DiscoverySummary> findPublicSummaries(int limit, int offset) {
        page(limit, offset);
        return JdbcTransactions.run(connections, false, connection -> {
            var summaries = new ArrayList<DiscoverySummary>();
            try (var statement = connection.prepareStatement("SELECT " + DiscoveryRows.SUMMARY_COLUMNS
                    + " FROM discoveries WHERE visibility = 'PUBLIC' ORDER BY fingerprint LIMIT ? OFFSET ?")) {
                statement.setInt(1, limit); statement.setInt(2, offset);
                try (var result = statement.executeQuery()) {
                    while (result.next()) summaries.add(DiscoveryRows.summary(result));
                }
            }
            return List.copyOf(summaries);
        });
    }

    public RecipeKnowledge knowledgeFor(DrinkFingerprint fingerprint, UUID player) {
        Objects.requireNonNull(player);
        return findByFingerprint(fingerprint).<RecipeKnowledge>map(stored -> stored.discovery().knowledgeFor(player))
                .orElseGet(() -> new RecipeKnowledge.Unknown(player));
    }

    private static boolean constraintViolation(StorageException failure) {
        return failure.getCause() instanceof SQLException sql && (sql.getErrorCode() == 19
                || sql.getSQLState() != null && sql.getSQLState().startsWith("23"));
    }

    private static void page(int limit, int offset) {
        if (limit < 1 || limit > 200 || offset < 0) throw new IllegalArgumentException("invalid pagination");
    }
}
