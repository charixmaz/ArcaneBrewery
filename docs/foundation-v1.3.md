# Foundation V1.3: persistence and repository layer

## Architecture and default backend

The pure `domain` package has no JDBC, SQLite, MySQL, Bukkit or Paper imports.
`application.storage` defines `DiscoveryRepository`, `StorageProvider`,
`StoredDiscovery`, explicit failure types and registration results.
`DiscoveryRegistrationService` registers completed processes through that abstraction.
The `storage` package contains explicit codecs, JDBC transactions/row mapping,
SQLite migrations/provider, and an opt-in MySQL/MariaDB adapter.

`StorageProviders.openDefault(dataDirectory)` opens SQLite at
`dataDirectory.resolve("arcanebrewery.db")` and creates missing parent directories.
The integration caller may supply its plugin data directory; storage has no Bukkit
path assumptions. `SqliteStorageProvider(databaseFile)` also accepts a direct injected Path.
No storage is initialized by the current plugin entry point, listeners or machines.
No gameplay code, commands, economy or GUI changed.

Connections are opened and closed per operation. Provider close blocks subsequent
operations, including retained repository references; it does not own background
threads or a shared registry. Shutdown should first drain in-flight application work.
SQLite uses WAL, a 10-second busy timeout and foreign keys checked on every
connection. Writes/migrations use IMMEDIATE transactions to serialize writers;
multi-query aggregate reads use DEFERRED transactions for consistent snapshots.
All SQL execution uses prepared statements; user values are bound parameters,
including names, UUIDs, fingerprints, binary processes and grant payloads.

## SQLite schema V1

| Table | Key and contents |
| --- | --- |
| schema_migrations | Integer version primary key and SHA-256 migration checksum |
| discoveries | Fingerprint primary key; owner UUID, process BLOB, exact Instant text, temporary/canonical names, visibility, revision |
| recipe_grants | Composite fingerprint/recipient key; FULL/PARTIAL kind and nullable versioned field selection |

The grants table references discoveries with a foreign key and ON DELETE CASCADE.
There is deliberately no repository deletion API; cascading is only a schema
integrity rule, not application permission to delete discoveries.
Indexes support owner/fingerprint, visibility/fingerprint and recipient/fingerprint
lookups. Fingerprint uniqueness, visibility/kind/revision checks and process/grant
payload bounds are enforced in SQL as well as application validation.
Recipient grants are replaced atomically with aggregate metadata. FULL grants
have no partial payload. PARTIAL grants persist the exact selected field vocabulary;
details are reconstructed from the immutable stored process without broadening access.
The optional canonical name's presence retains the one-time-rename-used state.

## Process and grant codecs

`ProcessProfileCodec` V1 is an explicit binary DTO format:

1. Four-byte ABPC magic and a four-byte version, both big-endian.
2. Sorted ingredient amounts, preparation text, fermentation fields, distillation
   fields, aging fields and sorted additive amounts, in that fixed order.
3. Maps have integer entry counts; text is integer-byte-length-prefixed UTF-8.
   Temperatures/amounts use IEEE-754 doubles; durations use longs; passes use an int.

All process fields, including non-identity batch conditions, round-trip exactly.
Payloads are limited to 1 MiB, each text value to 4096 UTF-8 bytes, and each map to
1024 entries. Decode rejects malformed UTF-8, invalid values/counts, duplicates,
truncation, trailing bytes and noncanonical encodings. Unsupported versions fail
explicitly. No Java native serialization, class names or Bukkit serialization is used.
Any future codec version needs an explicit decoder and migration; unknown versions
are never guessed or silently downgraded.

`ProcessProfile` is now a final immutable value class with the same constructor,
accessors and structural equality. The normal constructor retains V1.1 ratio
normalization and fingerprint behavior. `restoreCanonical` validates/copies already
normalized maps without running a second floating-point normalization pass; this
preserves every stored double and fingerprint. A sum tolerance of 1e-12 accommodates
normal binary rounding, while negative/nonfinite quantities and noncanonical IDs fail.
The codec requires byte-identical re-encoding after validated restoration.

`RecipeGrantCodec` V1 stores sorted, stable `ProcessField` vocabulary identifiers
as `1:FIELD_A,FIELD_B`, with a 512-character bound. Class names are not serialized.
Future changes to these identifiers require an explicit codec migration. Unknown,
duplicate, complete, empty or noncanonical partial selections fail explicitly.

`DiscoverySnapshot`/`RecipeGrant` are immutable domain reconstitution values.
Restoration validates process/fingerprint agreement, recipient/owner separation,
visibility/grant consistency and naming invariants. Historical temporary names are
restored from storage, not regenerated under potentially changed naming rules.
Persisted records are never silently skipped or reset when corrupt.
Storage failures use generic messages and do not log secret process payloads.

## Revision checks and immutable origin

New records start at revision 0. `update(expectedRevision, discovery)` performs
one conditional UPDATE and increments the revision only on success. A mismatch
throws `OptimisticLockException`; callers must reload and deliberately reapply
their intended operation. Missing records likewise fail rather than being inserted.
Revision overflow is rejected.

The update predicate also preserves the original owner, exact stored process,
discovery timestamp and temporary name. Once a canonical name is set, it cannot
be replaced or removed even if a caller supplies the latest revision with a
reconstructed snapshot. Identity is never derived from a presentation name.
All metadata and grant changes commit or roll back together. A failed grant insert
cannot leave a consumed rename, incremented revision, changed visibility or lost grant.

Revisions protect legitimate callers from stale updates, not authenticated owner
intent: an owner can intentionally grant access again using the latest snapshot.
Application code must retain the revision alongside its loaded aggregate.

## First-discovery arbitration

`DiscoveryRegistrationService.register(player, process, completedAt)` creates
a PRIVATE candidate and attempts a database INSERT without a preceding existence
check. The primary key chooses exactly one original owner across concurrent callers.
A conflicting attempt rolls back and reads the winner from a fresh transaction,
returning ALREADY_DISCOVERED. REGISTERED/ALREADY_DISCOVERED results include only
fingerprint and original owner, never the stored recipe or grants.
Other constraint failures and operational errors are not treated as successful
registration. Lock timeouts remain storage failures that application policy may retry.
Rediscovery does not transfer ownership or modify the existing record.

## Queries, secrecy and application boundary

Trusted application APIs load exact aggregates by fingerprint, check existence,
or page through discoveries owned by a UUID. Pages accept 1..200 results and a
nonnegative offset, ordered by fingerprint. Owner loading currently uses a bounded
per-record aggregate lookup; a batch query can optimize large catalogs later.

Public summary queries select metadata columns only and only PUBLIC records.
They never select process BLOBs or grant rows. `knowledgeFor(fingerprint, player)`
returns V1.2 projections; missing/private discoveries yield UNKNOWN for unauthorized
players, public discoveries yield IDENTIFIED, and grants give PARTIAL/FULL as before.
Repository aggregate methods and snapshots remain trusted APIs, not network response
models. Authentication must supply actor/player UUIDs; storage is not authentication.
Revocation cannot erase previously received information.

## Migration rules

SQLite V1 creates the discovery/grant tables and indexes. `MigrationRunner`
requires consecutive migrations from V1, in order. It verifies all recorded
checksums and rejects gaps, changed historical migrations and newer schemas.
Each invocation holds a database write transaction across history checks, pending
DDL and history inserts. Already-applied migrations do not rerun. Any failed
statement rolls back the entire invocation, including new DDL and history.
`StorageProvider.schemaVersion()` exposes the recorded version.
Applied migration SQL must never be edited in place; add V2 and preserve V1.

## Optional MySQL/MariaDB strategy

`MySqlStorageProvider(DataSource)` is an opt-in implementation shape reusing
portable JDBC DML with REPEATABLE_READ aggregate snapshots. A configured DataSource
and externally provisioned schema V1 are required; neither is needed for SQLite
or plugin startup. The caller owns the pool lifecycle. No MySQL/MariaDB driver
is added, bundled, downloaded or connected automatically.

`docs/mysql-v1.sql` provides the provisioning shape: InnoDB, binary fingerprint/
UUID collations, utf8mb4 names, bounded BLOBs, indexes, foreign keys and schema tracking.
Use a separate operator-managed migration tool. MySQL DDL can implicitly commit,
so the SQLite transactional DDL runner must not be reused for it.
See the [MySQL implicit-commit documentation](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html).
If deployment adds Connector/J or the MariaDB driver, keep it runtime-scoped and
optional in that deployment; do not add it as a required plugin startup dependency.
Live MySQL/MariaDB integration and server/version certification are deferred.
SQLite is the tested, production-targeted backend for this milestone.

## Dependencies and verification

Added `org.xerial:sqlite-jdbc:3.53.4.0` with runtime scope, bundling SQLite 3.53.4;
see the [upstream release](https://github.com/xerial/sqlite-jdbc/releases/tag/3.53.4.0).
The shade build keeps native resources, merges service descriptors, removes stale
dependency signatures and avoids a generated dependency-reduced POM.
No JSON library, connection pool, MySQL driver or external service was added.
SQLite needs writable local storage and a supported native platform. Java may
require `--enable-native-access=ALL-UNNAMED` as native-access policy tightens.
WAL databases should live on local disks; backups must account for WAL sidecars
or use SQLite's supported backup/checkpoint process.

Tests use temporary databases and cover exact process/discovery/name/grant/visibility
round trips, 500 varied floating-point maps, revisions, stale and competing writes,
uniqueness arbitration, rollback, migrations/checksums/idempotence, lookups,
reopening, corruption, Unicode and SQL-injection-like data.
The packaged-JAR smoke test opens the shaded driver without Paper on the classpath
and queries the actual SQLite engine version.
`mvn clean verify` runs with Java 25. This workspace's existing target directory
has inherited Windows delete-deny permissions, so final verification uses a fresh
temporary copy of byte-identical source/POM files, as in V1.1/V1.2.

Future Bukkit integration must explicitly initialize/close the provider, schedule
blocking JDBC work off event threads, persist revisions with snapshots, and serve
only knowledge/summary projections. This milestone does not wire those paths.
