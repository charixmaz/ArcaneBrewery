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
- idempotent deposit/withdraw/set/transfer operations
- atomic transfers
- Bukkit ServicesManager API registration
- database work kept off the main thread by built-in commands
- shaded SQLite driver

## Commands

- /money [player]
- /pay <online-player> <amount>
- /eco give <online-player> <amount>
- /eco take <online-player> <amount>
- /eco set <online-player> <amount>

The initial command layer intentionally targets online players only. The Java API is UUID-based and can operate on offline accounts.

## Java service API

Other Paper plugins may obtain:

`ArcaneEconomyService`

from Bukkit's ServicesManager when ArcaneEconomy is present.

Operations:
- balance(UUID)
- deposit(UUID, BigDecimal, transactionId)
- withdraw(UUID, BigDecimal, transactionId)
- setBalance(UUID, BigDecimal, transactionId)
- transfer(UUID, UUID, BigDecimal, transactionId)

Mutating integrations should generate and persist stable transaction IDs for retries. Replaying the same transaction ID cannot apply the mutation twice.

## Storage

SQLite schema V1:

- economy_meta
- economy_accounts
- economy_transactions

Balances are stored as signed 64-bit integer minor units rather than floating point.

For a scale of 2:
- 1.00 = 100 minor units
- 12.34 = 1234 minor units

This avoids floating-point money errors.

## Configuration

See src/main/resources/config.yml.

The plugin fails closed at startup when money/storage settings are malformed.

## Roadmap

Before a public release:
- durable migration framework beyond schema V1
- transaction history/query API
- offline-safe name/UUID command resolution
- audit/admin history commands
- optional MySQL/MariaDB backend
- compatibility adapters where useful
- ArcaneBrewery EconomyPort adapter
- live Paper concurrency/load testing
- public API versioning guarantees
