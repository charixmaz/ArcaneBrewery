# Foundation V1.19: market pricing and transaction boundaries

V1.19 builds a safe market layer on top of V1.18 stored drink value.

It still does not require Vault and does not expose a player shop yet.

## Pricing profile

The market profile defines:
- player sell multiplier;
- player buy multiplier;
- minimum market price;
- maximum market price;
- quote lifetime.

Default values:
- sell multiplier: 0.75;
- buy multiplier: 1.25;
- minimum price: 0.01;
- maximum price: 100000;
- quote lifetime: 30 seconds.

The buy multiplier cannot be lower than the sell multiplier.

All values are validated at startup.

## Market quotes

A MarketQuote is immutable and versioned.

A quote captures:
- unique quote ID;
- canonical drink fingerprint;
- immutable market-asset token;
- side: player sells or player buys;
- stored intrinsic V1.18 value;
- quoted price;
- creation timestamp;
- expiration timestamp.

Quotes expire and cannot be settled after their expiration time.

A quote is bound to one exact market asset. Modifying the stored value data changes the asset token and invalidates the quote.

## Canonical market asset

A MarketAsset is derived from:
- the canonical drink fingerprint;
- the complete stored DrinkValue breakdown.

The token is a deterministic SHA-256 digest over those fields.

The market token is not recipe identity and is not written into DrinkFingerprint.

Its purpose is transaction binding.

## Bottle validation

CanonicalMarketBottleReader accepts only:
- one POTION item;
- current canonical finished bottle format V4;
- FINISHED stage;
- a valid lowercase 64-character SHA-256 fingerprint;
- complete valid V4 DrinkValue metadata;
- complete canonical consumption metadata;
- complete canonical alchemy metadata.

Stacked bottles, legacy V3 bottles, malformed metadata, incomplete metadata and invalid fingerprints fail closed.

Legacy bottles are therefore not silently assigned market prices.

## Transaction boundary

V1.19 introduces ports rather than a hard dependency on an economy plugin:

- EconomyPort
- MarketAssetPort
- MarketTransactionJournal

Sale settlement order is:

1. validate quote side, asset binding and expiration;
2. atomically reserve the transaction ID in the journal;
3. remove the exact bound asset from player inventory;
4. deposit the quoted amount through EconomyPort;
5. commit the transaction journal record.

If item removal fails, no deposit occurs.

If deposit fails, the asset-restoration path is invoked and the transaction is rejected.

Duplicate transaction IDs are rejected before inventory or economy mutation.

The same transaction ID is passed through the journal, asset and economy ports so a future persistent adapter can provide idempotent reconciliation.

## Important persistence rule

The transaction journal must be durable and atomic in a production market implementation.

V1.19 defines that contract but does not yet provide a SQLite market journal or Vault adapter.

An in-memory implementation would not be sufficient for a production economy because server crashes could replay incomplete transactions.

## Buy-side scope

V1.19 provides deterministic PLAYER_BUYS quotes, but does not yet implement purchase settlement/item creation.

Purchase settlement requires a canonical item-creation source and refund/reconciliation behavior and should be added together with the persistent transaction journal rather than approximated now.

## Configuration

New config.yml values:

market:
  sell-multiplier: 0.75
  buy-multiplier: 1.25
  minimum-price: 0.01
  maximum-price: 100000.0
  quote-lifetime-seconds: 30.0

Invalid market configuration fails plugin startup.

## Verification

Tests cover:
- configured buy/sell spread;
- quote asset binding;
- quote expiry;
- minimum/maximum price caps;
- invalid pricing profile rejection;
- successful sale settlement;
- duplicate transaction rejection;
- stale/mismatched quote rejection before mutation;
- failed deposit asset restoration;
- failed item removal preventing deposit;
- strict V4 bottle acceptance;
- stacked/non-potion rejection;
- legacy/incomplete metadata rejection;
- malformed fingerprint rejection;
- default and custom market configuration;
- invalid market configuration.

The next economy milestone should implement the durable SQLite transaction journal and Bukkit inventory asset adapter first. Optional Vault integration should come only after that durable reconciliation layer exists.
