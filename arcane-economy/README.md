# ArcaneEconomy

ArcaneEconomy is a standalone Paper-first economy plugin developed alongside ArcaneBrewery.

It is intentionally separate from ArcaneBrewery. Servers may run:
- ArcaneBrewery without an economy plugin;
- ArcaneEconomy by itself;
- ArcaneBrewery + ArcaneEconomy;
- ArcaneBrewery with another economy provider through a future adapter.

## Current foundation

- Java 25 / Paper 26.1.x
- SQLite default storage
- WAL mode
- exact integer minor-unit storage
- BigDecimal public API
- configurable currency scale (0–6)
- configurable minimum, maximum and starting balances
- globally unique transaction IDs
- request-fingerprinted replay protection
- idempotent deposit/withdraw/set/transfer operations
- serialized mutation boundary inside one server process
- atomic transfers
- durable transaction history
- schema V1 -> V2 migration
- refusal of unknown future schemas
- Bukkit ServicesManager API registration
- database work kept off the main thread by built-in commands
- shaded SQLite driver

## Commands

- /money [player]
- /pay <online-player> <amount>
- /eco give <online-player> <amount>
- /eco take <online-player> <amount>
- /eco set <online-player> <amount>
- /eco history <online-player> [limit]

The initial command layer intentionally targets online players only. The Java API is UUID-based and can operate on offline accounts.

## Java service API

The public types now live in the independent
[`arcane-economy-api`](../arcane-economy-api/README.md) Maven artifact. ArcaneEconomy
depends on and packages that artifact; integration plugins use `provided` scope.
ArcaneEconomy does not depend on ArcaneBrewery. ArcaneBrewery's optional adapter
uses the shared service without packaging any economy API/implementation classes.

Build all projects from the repository root:

```sh
mvn --batch-mode -f arcane-economy-api/pom.xml clean install
mvn --batch-mode clean verify
mvn --batch-mode -f arcane-economy/pom.xml clean verify
python3 scripts/verify-economy-packaging.py
```

The API may also be verified independently with `clean verify`. Installing the API
is a local build prerequisite; no external service or mandatory server plugin was
added. CI verifies all three projects and publishes three separate artifacts.
Install only the desired plugin JARs on the server, not the API JAR.

Supported runtime: Java 25, ordinary Paper 26.1.x Bukkit plugin loading. The
provider owns the runtime API classloader. Consumers use `softdepend`, guarded
lazy API linkage and lifecycle-aware ServicesManager bindings. Same-classloader
disable/re-enable and service re-registration are handled; arbitrary hot JAR
replacement requires a server restart. Already executing ledger operations may
finish during disable; new calls through retained ledger references fail closed.

ArcaneBrewery starts normally without the provider. Its settlement entry point
schedules journal/economy calls on a bounded dedicated worker and inventory
operations on the server thread. It does not introduce a GUI, marketplace command,
durable market journal or player-inventory asset adapter. Those remain explicit
prerequisites for exposing production sales. See the
[integration review](../docs/economy-api-integration.md).

Other Paper plugins may obtain:

`ArcaneEconomyService`

from Bukkit's ServicesManager when ArcaneEconomy is present.

Operations:
- balance(UUID)
- deposit(UUID, BigDecimal, transactionId)
- withdraw(UUID, BigDecimal, transactionId)
- setBalance(UUID, BigDecimal, transactionId)
- transfer(UUID, UUID, BigDecimal, transactionId)
- transaction(transactionId)
- history(UUID, limit)

The service API performs synchronous storage work. Integrating plugins should call it away from the Paper main thread unless they provide their own safe scheduling boundary.

Mutating integrations should generate and persist stable transaction IDs for retries.

Replaying the same transaction ID with the same request returns a duplicate result and cannot apply the mutation twice.

Reusing the same transaction ID for a different amount, operation, source or target is rejected as invalid.

Treat `APPLIED` and a verified exact `DUPLICATE` as successful settlement, but never
`INVALID`, `OUT_OF_RANGE` or `INSUFFICIENT_FUNDS`. If a write throws, its result may
be uncertain: reconcile `transaction(originalId)` before restoring an asset or
retrying. The API README documents the full threading and idempotency contract.

## Storage

SQLite schema V2:

- economy_meta
- economy_accounts
- economy_transactions

V2 adds:
- request hashes for replay verification;
- target post-transfer balance storage;
- source/history indexes;
- target/history indexes.

Balances are stored as signed 64-bit integer minor units rather than floating point.

For a scale of 2:
- 1.00 = 100 minor units
- 12.34 = 1234 minor units

This avoids floating-point money errors.

Schema migration is transactional. Existing V1 balances and transactions are preserved when upgrading to V2.

If a database claims a schema version newer than this plugin understands, startup fails instead of rewriting it.

## Concurrency

All balance-mutating operations on one ArcaneEconomy service instance are serialized before entering SQLite.

This prevents two async server tasks from racing through the same balance snapshot.

SQLite transactions still provide the durable atomicity boundary.

Reads may proceed independently.

The current design assumes one ArcaneEconomy plugin process owns one SQLite database file. Sharing the same SQLite file between multiple Minecraft server JVMs is not supported.

## Configuration

See src/main/resources/config.yml.

The plugin fails closed at startup when money/storage settings are malformed.

## Verification

Automated tests cover:
- exact minor-unit conversion;
- scale enforcement;
- minimum/maximum balance bounds;
- restart persistence;
- duplicate transaction replay;
- same-ID/different-request rejection;
- concurrent duplicate retries applying once;
- atomic transfers;
- failed-transfer rollback;
- transaction lookup/history;
- target/source post-transfer balances;
- V1 -> V2 migration with preserved data;
- unknown future schema rejection;
- path traversal and config validation.

## Roadmap

Before a public release:
- transaction pagination / richer audit queries
- offline-safe name/UUID command resolution
- stronger admin audit tooling
- optional MySQL/MariaDB backend
- compatibility adapters where useful
- ArcaneBrewery EconomyPort adapter
- live Paper concurrency/load testing
- public API versioning guarantees
