# ArcaneAlchemy upgrade and access

## Installation

Back up worlds, player data and plugin data. Stop Paper, remove the old ArcaneBrewery JAR and install `target/ArcaneAlchemy-1.0-SNAPSHOT.jar`. Never load both plugins. First startup copies the legacy data folder into ArcaneAlchemy and retains the original; a populated conflicting target blocks startup rather than selecting a fresh database. An interrupted copy also fails closed and requires recovery from backup. The database filename, Java packages, `arcanebrewery` metadata namespace and existing permission nodes intentionally remain unchanged.

The public name change is branding, not an EULA compliance guarantee. The repository URL remains unchanged.

## Commands and portable Codex

`/al` aliases `/arcanealchemy` for both players and admins. `/al guide` gives an Alchemy Codex item. Hold it and right-click to open the existing private GUI. If inventory is full, make room and retry. Re-running the command does not grant another book if one is already carried. The `codex` subcommand is removed. Legacy `/brew` and `/arcanebrewery` aliases remain for transition; visible help uses `/al`. `/al market` routes historical diagnostics for admins with the existing admin permission; it remains read-only.

## Owner-managed operation access

- `/al access grant all Player`: all machines owned by the caller, including future machines.
- `/al access grant cultivation Player`: all Cultivation Barrels owned by the caller.
- Other categories: `extractor`, `infuser`, `cask`, `specialist`.
- `/al access grant machine Player`: the claimed core the owner is looking directly at within six blocks.
- `/al access revoke <scope> Player`: remove that grant.
- `/al access list`: list the caller's grants.

The target must be online so the UUID is verified. Grants persist in `machine-access.properties` and work when the owner is offline. They authorize operation, not transfer, granting on the owner's behalf or structure destruction. Existing exclusive-viewer sessions and input locks remain. Access changes close the owner's active machine views so revoked access cannot keep an old session. Machine owner descriptions list up to five relevant permitted names; the command lists all recorded grants. World claim/protection denials still apply. Personal research requirements remain in force; access grants do not purchase XP unlocks.

## Structure and UI feedback

The cultivation build remains a horizontal Barrel plus a wooden Fence directly behind it. The current Codex includes this and the Smooth Stone processor foundation. New maturation signs use `[Alchemy]`; old `[Brewery]` signs remain recognized. Right-click updates the sign to ACTIVE for a valid structure or INVALID otherwise. Invalid structures do not open a cask GUI. The label is refreshed by interaction, not a continuous world-wide scanner, and does not indicate maturity.

Production backgrounds use light-blue glass, specialists use cyan; actual item input squares remain empty. These are vanilla icons, with no resource pack requirement.

## Verification

Automated migration, grant persistence/scope and access-control checks accompany the change. Native Paper upgrade, book interactions, sign appearance and protection-plugin compatibility must be live-tested separately. No EULA is accepted automatically.
