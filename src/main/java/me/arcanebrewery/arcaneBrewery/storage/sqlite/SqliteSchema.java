package me.arcanebrewery.arcaneBrewery.storage.sqlite;

import me.arcanebrewery.arcaneBrewery.storage.migration.SqlMigration;
import java.util.List;

public final class SqliteSchema {
    private SqliteSchema() {}
    public static final int VERSION = 5;

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
                )),
                new SqlMigration(3, List.of(
                    "CREATE TABLE personal_recipes (identity TEXT NOT NULL, player_id TEXT NOT NULL, process_payload BLOB, recorded_at TEXT NOT NULL, temporary_name TEXT NOT NULL, personal_name TEXT, visibility TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0, provenance TEXT NOT NULL, produced INTEGER NOT NULL DEFAULT 0, best_quality REAL, PRIMARY KEY(identity,player_id))",
                    "CREATE TABLE personal_grants (identity TEXT NOT NULL, source_id TEXT NOT NULL, recipient_id TEXT NOT NULL, kind TEXT NOT NULL, selection_payload TEXT, legacy_source TEXT, modified INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(identity,source_id,recipient_id))",
                    "CREATE INDEX personal_recipient_idx ON personal_grants(recipient_id,identity)",
                    "CREATE TABLE server_firsts (identity TEXT PRIMARY KEY NOT NULL, player_id TEXT NOT NULL, discovered_at TEXT NOT NULL)",
                    "CREATE TABLE recipe_aliases (legacy_fingerprint TEXT PRIMARY KEY NOT NULL, identity TEXT NOT NULL)",
                    "CREATE TABLE production_batches (batch_key TEXT PRIMARY KEY NOT NULL, identity TEXT NOT NULL, player_id TEXT NOT NULL, process_payload BLOB NOT NULL, completed_at TEXT NOT NULL, quality REAL NOT NULL DEFAULT 0, evidence_payload BLOB)",
                    "CREATE TABLE recipe_process_records (fingerprint TEXT PRIMARY KEY NOT NULL, process_payload BLOB NOT NULL)",
                    "CREATE TABLE gameplay_migrations (version INTEGER PRIMARY KEY NOT NULL)"
                )),
                new SqlMigration(4,List.of(
                    "CREATE TABLE material_discoveries (player_id TEXT NOT NULL CHECK(length(player_id)=36), kind TEXT NOT NULL CHECK(kind IN ('INGREDIENT','CATALYST')), registry_id TEXT NOT NULL, obtained_at TEXT NOT NULL, PRIMARY KEY(player_id,kind,registry_id))"
                )),
                new SqlMigration(5,List.of(
                    "ALTER TABLE personal_recipes ADD COLUMN rename_used INTEGER NOT NULL DEFAULT 0 CHECK(rename_used IN (0,1))",
                    "UPDATE personal_recipes SET rename_used=1 WHERE personal_name IS NOT NULL",
                    "CREATE TRIGGER personal_rename_guard BEFORE UPDATE OF personal_name,rename_used ON personal_recipes WHEN OLD.rename_used=1 AND (NEW.personal_name IS NOT OLD.personal_name OR NEW.rename_used<>1) BEGIN SELECT RAISE(ABORT,'Personal formulation already permanently renamed'); END",
                    "CREATE TRIGGER personal_rename_mark AFTER UPDATE OF personal_name ON personal_recipes WHEN NEW.personal_name IS NOT NULL AND NEW.rename_used=0 BEGIN UPDATE personal_recipes SET rename_used=1 WHERE identity=NEW.identity AND player_id=NEW.player_id; END",
                    "CREATE TRIGGER personal_rename_insert AFTER INSERT ON personal_recipes WHEN NEW.personal_name IS NOT NULL AND NEW.rename_used=0 BEGIN UPDATE personal_recipes SET rename_used=1 WHERE identity=NEW.identity AND player_id=NEW.player_id; END"
                ))
        );
    }
}
