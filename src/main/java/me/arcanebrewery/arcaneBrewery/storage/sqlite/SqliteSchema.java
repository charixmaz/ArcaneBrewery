package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.storage.migration.SqlMigration;
import java.util.List;

public final class SqliteSchema {
    private SqliteSchema() {}
    public static final int VERSION = 2;

    public static List<SqlMigration> migrations() {
        return List.of(
                new SqlMigration(1, List.of(
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
                )),
                new SqlMigration(2, List.of(
                        """
                        CREATE TABLE market_transactions (
                          transaction_id TEXT PRIMARY KEY NOT NULL CHECK(length(transaction_id) = 36),
                          player_id TEXT NOT NULL CHECK(length(player_id) = 36),
                          quote_version INTEGER NOT NULL,
                          quote_id TEXT NOT NULL CHECK(length(quote_id) = 36),
                          fingerprint TEXT NOT NULL CHECK(length(fingerprint) = 64),
                          asset_token TEXT NOT NULL CHECK(length(asset_token) = 64),
                          side TEXT NOT NULL CHECK(side IN ('PLAYER_SELLS','PLAYER_BUYS')),
                          intrinsic_value TEXT NOT NULL,
                          price TEXT NOT NULL,
                          quote_created_at INTEGER NOT NULL CHECK(quote_created_at >= 0),
                          quote_expires_at INTEGER NOT NULL CHECK(quote_expires_at > quote_created_at),
                          status TEXT NOT NULL CHECK(status IN ('PREPARED','COMMITTED','REJECTED')),
                          reason TEXT NOT NULL DEFAULT '',
                          created_at INTEGER NOT NULL CHECK(created_at >= 0),
                          updated_at INTEGER NOT NULL CHECK(updated_at >= created_at)
                        )
                        """,
                        """
                        CREATE TABLE market_escrow (
                          transaction_id TEXT PRIMARY KEY NOT NULL,
                          player_id TEXT NOT NULL CHECK(length(player_id) = 36),
                          asset_token TEXT NOT NULL CHECK(length(asset_token) = 64),
                          inventory_slot INTEGER NOT NULL CHECK(inventory_slot BETWEEN 0 AND 40),
                          state TEXT NOT NULL CHECK(state IN ('STAGED','REMOVED','RESTORED','RELEASED','RECONCILE')),
                          item_payload BLOB NOT NULL CHECK(length(item_payload) BETWEEN 1 AND 1048576),
                          created_at INTEGER NOT NULL CHECK(created_at >= 0),
                          updated_at INTEGER NOT NULL CHECK(updated_at >= created_at),
                          FOREIGN KEY(transaction_id) REFERENCES market_transactions(transaction_id) ON DELETE CASCADE
                        )
                        """,
                        "CREATE INDEX market_transactions_status_idx ON market_transactions(status, updated_at)",
                        "CREATE INDEX market_transactions_player_idx ON market_transactions(player_id, updated_at)",
                        "CREATE INDEX market_escrow_state_idx ON market_escrow(state, updated_at)"
                ))
        );
    }
}
