package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.storage.codec.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Explicit row mapping. Corrupt rows fail instead of being skipped or reset to defaults. */
final class DiscoveryRows {
    static final int MAX_GRANTS = 10_000;
    static final String SUMMARY_COLUMNS = "fingerprint, owner_id, discovered_at, temporary_name, canonical_name, visibility";
    private final ProcessProfileCodec processes = new ProcessProfileCodec();
    private final RecipeGrantCodec grants = new RecipeGrantCodec();

    Optional<StoredDiscovery> load(Connection connection, DrinkFingerprint fingerprint) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + SUMMARY_COLUMNS
                + ", process_payload, LENGTH(process_payload) AS payload_size, revision FROM discoveries WHERE fingerprint = ?")) {
            statement.setString(1, fingerprint.value());
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                try {
                    if (result.getLong("payload_size") > ProcessProfileCodec.MAX_PAYLOAD_BYTES)
                        throw new CorruptRecordException("Process payload exceeds storage limit");
                    var process = processes.decode(result.getBytes("process_payload"));
                    var savedFingerprint = fingerprint(result.getString("fingerprint"));
                    var owner = uuid(result.getString("owner_id"));
                    var time = Instant.parse(result.getString("discovered_at"));
                    var temporary = name(result.getString("temporary_name"));
                    var canonical = Optional.ofNullable(result.getString("canonical_name")).map(DiscoveryRows::name);
                    var visibility = DiscoveryVisibility.valueOf(result.getString("visibility"));
                    long revision = result.getLong("revision");
                    var snapshot = new DiscoverySnapshot(owner, savedFingerprint, process, time, temporary,
                            canonical, visibility, loadGrants(connection, savedFingerprint));
                    return Optional.of(new StoredDiscovery(PlayerDiscovery.restore(snapshot), revision));
                } catch (IllegalArgumentException | NullPointerException e) {
                    throw new CorruptRecordException("Invalid persisted discovery", e);
                }
            }
        }
    }

    private Map<UUID, RecipeGrant> loadGrants(Connection connection, DrinkFingerprint fingerprint) throws SQLException {
        var values = new HashMap<UUID, RecipeGrant>();
        try (var statement = connection.prepareStatement(
                "SELECT recipient_id, kind, selection_payload FROM recipe_grants WHERE fingerprint = ? LIMIT 10001")) {
            statement.setString(1, fingerprint.value());
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    if (values.size() == MAX_GRANTS) throw new CorruptRecordException("Too many persisted grants");
                    UUID recipient = uuid(result.getString("recipient_id"));
                    RecipeGrant grant = grants.decode(result.getString("kind"), result.getString("selection_payload"));
                    if (values.putIfAbsent(recipient, grant) != null)
                        throw new CorruptRecordException("Duplicate persisted grant");
                }
            }
        }
        return values;
    }

    void writeGrants(Connection connection, DiscoverySnapshot snapshot) throws SQLException {
        if (snapshot.grants().size() > MAX_GRANTS) throw new IllegalArgumentException("too many recipe grants");
        try (var statement = connection.prepareStatement(
                "INSERT INTO recipe_grants(fingerprint, recipient_id, kind, selection_payload) VALUES (?, ?, ?, ?)")) {
            for (var entry : snapshot.grants().entrySet()) {
                statement.setString(1, snapshot.fingerprint().value());
                statement.setString(2, entry.getKey().toString());
                statement.setString(3, entry.getValue().full() ? "FULL" : "PARTIAL");
                statement.setString(4, grants.encode(entry.getValue()));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    static DiscoverySummary summary(ResultSet result) throws SQLException {
        try {
            var temporary = name(result.getString("temporary_name"));
            var canonical = Optional.ofNullable(result.getString("canonical_name")).map(DiscoveryRows::name);
            return new DiscoverySummary(uuid(result.getString("owner_id")), fingerprint(result.getString("fingerprint")),
                    Instant.parse(result.getString("discovered_at")), canonical.orElse(temporary),
                    DiscoveryVisibility.valueOf(result.getString("visibility")));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CorruptRecordException("Invalid persisted discovery metadata", e);
        }
    }

    static UUID uuid(String value) {
        UUID uuid = UUID.fromString(value);
        if (!uuid.toString().equals(value)) throw new CorruptRecordException("Noncanonical persisted UUID");
        return uuid;
    }

    private static DrinkFingerprint fingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new CorruptRecordException("Invalid persisted fingerprint");
        return new DrinkFingerprint(value);
    }

    private static DrinkName name(String value) {
        var name = new DrinkName(value);
        if (!name.value().equals(value)) throw new CorruptRecordException("Noncanonical persisted name");
        return name;
    }
}
