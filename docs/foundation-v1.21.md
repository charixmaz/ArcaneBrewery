# ArcaneBrewery V1.21 — Controlled player market entry

V1.21 exposes the V1.20 durable market backend through a deliberately narrow player/admin command surface.

## Safety defaults

Player selling is OFF by default.

```yaml
market:
  player-sales-enabled: false
```

Installing or updating ArcaneBrewery therefore never starts creating economy money automatically.

To sell drinks, all of these must be true:

1. `market.player-sales-enabled: true`
2. ArcaneEconomy is installed, enabled and has a registered compatible service
3. the player has `arcanebrewery.market.sell`
4. the player holds one current canonical finished drink bottle
5. a fresh sell quote exists
6. the player explicitly confirms it before expiry

`/brew value` remains read-only and works even when player selling is disabled.

## Player commands

### /brew value

Reads the single finished canonical drink in the player's main hand and shows:

- intrinsic ArcaneBrewery value units
- current server sell quote

No item, journal or economy state is mutated.

### /brew sell

Creates a temporary in-memory PLAYER_SELLS quote for the current main-hand drink.

The quote:

- is bound to the canonical MarketAsset
- uses the configured quote lifetime
- is replaced if the player requests another quote
- is cleared when the player disconnects
- does not remove the bottle
- does not create a transaction yet

### /brew sell confirm

Confirmation requires the same quoted MarketAsset to still be held in the main hand.

On confirmation:

- the pending quote is consumed
- a new transaction UUID is generated
- the durable V1.20 settlement path is invoked
- bottle escrow/inventory mutation happens on the server thread
- SQLite and economy work run off-thread
- success reports the exact quoted amount
- confirmed rejection reports failure
- uncertain outcomes show the player a short transaction ID and preserve records for reconciliation

A failed or uncertain settlement is never retried automatically with a new transaction ID.

## Dynamic provider availability

ArcaneEconomy remains optional.

ArcaneBrewery observes ArcaneEconomy's enable/disable lifecycle.

If the provider disappears:

- brewing remains enabled
- `/brew value` still works
- new sell quotes/confirmations fail closed

If ArcaneEconomy becomes available again, player selling can resume without restarting Brewery, assuming config and permissions allow it.

## Admin diagnostics

New permission:

`arcanebrewery.admin.market` (default: op)

New command:

### /brewmarket pending [limit]

Read-only report of:

- PREPARED Brewery market transactions
- unresolved escrow records
- short transaction/player identifiers
- quote price
- escrow state

### /brewmarket tx <transaction-uuid>

Read-only inspection of one transaction:

- journal status
- player UUID
- quote UUID
- fingerprint
- asset token
- price
- rejection reason when present
- escrow state
- original slot
- serialized payload size

V1.21 intentionally does NOT include an admin "force pay", "force refund" or "resolve" command.

Those actions require explicit reconciliation policy and live-server validation first.

## Permissions

- `arcanebrewery.use` — normal Brewery commands
- `arcanebrewery.market.sell` — player selling when globally enabled
- `arcanebrewery.admin.market` — read-only market recovery diagnostics

## Still intentionally deferred

- automatic ambiguous crash recovery
- force-refund/force-commit admin actions
- GUI market screen
- buy-side item creation
- Vault
- dynamic supply/demand pricing
- taxes and economy sinks
- daily/global sale limits
- player-to-player market
- live Paper certification

Before enabling `market.player-sales-enabled` on a production server, V1.20/V1.21 still require real Paper validation of inventory serialization, disconnects, shutdown windows and ArcaneEconomy transaction replay.
