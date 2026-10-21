package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.storage.migration.SqlMigration;
import java.util.List;

public final class SqliteSchema {
    private SqliteSchema() {}
    public static final int VERSION = 1;

    public static List<SqlMigration> migrations() {
        return List.of(new SqlMigration(1, List.of(
                """
                CREATE TABLE discoveries (
                  fingerprint TEXT PRIMARY KEY NOT NULL CHECK(length(fingerprint) = 64),
                  owner_id TEXT NOT NULL CHECK(length(owner_id) = 36),
                  process_payload BLOB NOT NULL CHECK(length(process_payload) <= 1048576),
                  discovered_at TEXT NOT NULL,
                  temporary_name TEXT NOT NULL,
                  canonical_name TEXT,
                  visibility TEXT NOT NULL CHECK(visibility IN ('PRIVATE','SHARED','PUBLIC')),
                  revision INTEGER NOT NULL CHECK(revision >= 0)
                )
                """,
                """
                CREATE TABLE recipe_grants (
                  fingerprint TEXT NOT NULL,
                  recipient_id TEXT NOT NULL CHECK(length(recipient_id) = 36),
                  kind TEXT NOT NULL CHECK(kind IN ('FULL','PARTIAL')),
                  selection_payload TEXT,
                  PRIMARY KEY(fingerprint, recipient_id),
                  FOREIGN KEY(fingerprint) REFERENCES discoveries(fingerprint) ON DELETE CASCADE,
                  CHECK((kind = 'FULL' AND selection_payload IS NULL)
                     OR (kind = 'PARTIAL' AND selection_payload IS NOT NULL AND length(selection_payload) <= 512))
                )
                """,
                "CREATE INDEX discoveries_owner_idx ON discoveries(owner_id, fingerprint)",
                "CREATE INDEX discoveries_visibility_idx ON discoveries(visibility, fingerprint)",
                "CREATE INDEX grants_recipient_idx ON recipe_grants(recipient_id, fingerprint)"
        )));
    }
}
