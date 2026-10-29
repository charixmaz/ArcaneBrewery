# Server-owner guide

## Requirements

Paper 26.1.2 and Java 25. Brewery is standalone; no economy API, resource pack or external database server is required. Other server implementations have not been certified.

## Installation and updates

Build with `mvn --batch-mode clean verify`, verify with `python3 scripts/verify-standalone-packaging.py`, then use `target/ArcaneBrewery-1.0-SNAPSHOT.jar`. Stop the server before replacing its JAR. Back up Brewery data, worlds and player data together before updating. Pull → rebuild → replace JAR → restart.

Database migrations use the existing backup mechanism. Never downgrade saved worlds/player data without restoring matching backups. Existing finished bottles retain their recorded statistics; active batches retain captured policy versions.

## Permissions

- `arcanebrewery.use`: player commands, default true.
- `arcanebrewery.rename.colors`: color-only permanent names, default false. Assign using your permission manager; Brewery does not sell ranks.
- `arcanebrewery.admin.bypass`: owner access bypass, default op; does not remove session/item-safety restrictions.
- `arcanebrewery.admin.market`: historical read-only diagnostics, default op. There are no payouts.

## Machines

Use `/brew guide` → Machines & Controls for block icons, construction resources and controls. The Reagent Processor uses Stonecutter directly above Smooth Stone. Old copper structures remain readable through every oxidation stage. Never break an occupied foundation to convert it; empty the machine first.

Specialist personal unlocks cost 50 XP levels each. Construction is separate and belongs to the physical machine. Recipes and exact processing records remain private.

## Pilot checks

Check startup logs; new and legacy bottles; restart during processing; full/partial inventories; second-player access; denied interactions with protection plugins; crafting and vanilla barrel behavior. Do not treat unit-test counts as a TPS or crash-safety guarantee. Record build SHA, Paper build, installed plugins, reproduction steps and logs in bug reports.

Consult CHANGELOG.md for released versus unreleased behavior. Live Paper startup is skipped in CI unless the owner provides the EULA acceptance setting.
