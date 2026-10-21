# ArcaneBrewery V1.20 — Durable market journal and bottle escrow

V1.20 adds Brewery-side persistence required before live player drink sales can be enabled.

## Storage schema V2

ArcaneBrewery SQLite schema advances from V1 to V2.

New tables:

- `market_transactions`
- `market_escrow`

Existing discovery tables and migration history are preserved.

### market_transactions

Stores the complete sale reservation:

- transaction UUID
- player UUID
- quote version and quote UUID
- drink fingerprint
- asset token
- market side
- intrinsic value
- quoted price
- quote timestamps
- PREPARED / COMMITTED / REJECTED state
- rejection reason
- journal timestamps

`JdbcMarketTransactionJournal` provides idempotent begin/commit/reject operations and recovery queries.

### market_escrow

Stores the exact serialized Paper ItemStack before inventory removal:

- transaction UUID
- player UUID
- asset token
- original inventory slot
- STAGED / REMOVED / RESTORED / RELEASED / RECONCILE state
- bounded item payload
- timestamps

The row is not deleted on success. RELEASED remains an auditable terminal state.

## Sale ordering

The durable sale path is:

1. validate quote and canonical market asset
2. create PREPARED transaction journal row
3. locate one deterministic matching canonical bottle
4. persist its exact serialized ItemStack as STAGED escrow
5. remove that bottle from the player inventory
6. mark escrow REMOVED
7. deposit exact quoted money through the EconomyPort using the original transaction UUID
8. mark escrow RELEASED
9. mark journal COMMITTED

Confirmed economy rejection restores the escrowed bottle and marks the journal REJECTED.

An uncertain economy write does not restore the bottle and does not reject the transaction.

## Crash semantics

SQLite, player inventory and an external economy provider cannot form one distributed ACID transaction.

V1.20 therefore fails closed rather than inventing atomicity.

Important crash windows:

- STAGED + bottle still present: escrow was persisted before inventory removal.
- STAGED + bottle absent: crash may have happened after removal but before REMOVED state.
- REMOVED + PREPARED: bottle definitely left inventory; payout may or may not have happened.
- RELEASED + PREPARED: payout path reached finalization, but journal commit may not have completed.
- RECONCILE: automatic action is intentionally prohibited.

At startup ArcaneBrewery counts PREPARED journal entries and unresolved escrow entries and emits a warning. It does not automatically pay or refund them.

Future admin reconciliation tooling must inspect the original transaction UUID and external economy receipt before resolving ambiguous entries.

## Bukkit asset adapter

`BukkitMarketAssetPort`:

- runs only on the Paper server thread
- accepts only a current canonical V4 finished potion through `CanonicalMarketBottleReader`
- removes one deterministic matching bottle
- serializes the exact ItemStack before mutation
- restores to the original slot when possible
- otherwise uses normal inventory insertion
- never silently drops a failed restoration on the ground
- marks malformed or mismatched escrow as RECONCILE
- supports multiple identical bottles by selecting one deterministic slot

## Runtime integration

ArcaneBrewery now has a built-in durable `settleMarketSale(player, asset, quote, transactionId)` path backed by:

- Brewery SQLite journal
- Brewery SQLite item escrow
- Bukkit inventory adapter
- optional ArcaneEconomy provider
- existing bounded market worker
- server-thread inventory handoff

No player-facing sell command or GUI is enabled yet.

## Verification

Automated coverage includes:

- schema V2 creation and indexes
- V1 -> V2 migration
- restart persistence for PREPARED transactions
- restart persistence for REMOVED escrow
- idempotent journal begin
- terminal journal transition protection
- manual reconciliation state
- successful settlement escrow finalization ordering
- existing market settlement/replay/threading tests

## Remaining live checks

Before enabling real player sales:

- real Paper ItemStack serialization/deserialization
- sell with one and multiple identical canonical bottles
- full-inventory failed-refund behavior
- player disconnect during server-thread handoff
- server shutdown in each STAGED/REMOVED/RELEASED window
- restart warning and manual inspection of unresolved records
- ArcaneEconomy payout replay using the same transaction UUID

V1.20 does not add Vault, player-facing shop commands, GUI, buy-side item creation or automatic ambiguous-transaction recovery.
