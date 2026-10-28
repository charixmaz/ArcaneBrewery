# Repository audit fixes — 28 October 2026

Baseline: master `ea7800775eb48e5b21d738d886793ba8ba7cbba3`.

## Changes players and owners will notice

| Area | New behavior |
| --- | --- |
| Discovery recording | A successful mature batch stays in its saved cask lane until its discovery write succeeds. Failed saves leave the batch available for another withdrawal attempt. |
| Retried harvests | The first harvest time and producer are saved on the retained source item. Retrying after a disconnect, closed menu or normal restart uses those same conditions. Another withdrawal does not age or reassign that harvest. |
| Optional sales | Escrow database operations run on the market worker. Only inventory inspection and mutation enter the server thread. |
| Sale selection | Quotes bind to the full selected bottle and hotbar slot. The selected item is checked at confirmation, before escrow staging, and again immediately before removal. An upgraded bottle elsewhere cannot be substituted. |
| Casks | Count each exact wood species separately. An earlier different wood block cannot hide twelve valid components. Oak and dark oak no longer combine, and stone does not count as wood. Two independently qualifying wood species are rejected as ambiguous. |
| Recipe instructions | Personal knowledge uses the player's highest-quality recorded production process, with a deterministic batch-key tie break. Original discovery records, private evidence, existing names and grants remain stored. |
| Bottle presentation | Coalesce inventory events within a tick, cache positive and negative name lookups for 30 seconds, deduplicate in-flight requests, and cap outstanding name lookups at 32. Renaming invalidates that producer's cached names. |

The cask interface labels retained harvests and explains safe retry. Discovery storage uses a dedicated bounded two-thread worker with shutdown draining. Closing a cask session suppresses any delayed delivery into that closed view.

The existing custom-journal/custom-inventory Java integration entry point retains its main-thread inventory contract. The built-in sale path uses a separate worker coordinator and preserves its existing four-argument entry point, capturing the current main-hand item; player quotes use the stricter explicit item/slot snapshot overload. No shared economy API change or provider rebuild is required.

## Persistence and upgrade

SQLite V6 adds only `production_best_process_idx` on production history. This keeps best-process retrieval indexed. V1–V5 migration definitions and checksums are unchanged. The existing storage provider takes a consistent pre-upgrade SQLite backup before migration. Existing recipes, permanent rename guards, production history and permissions are retained.

Harvest checkpoints are additive metadata on original cask items saved through the existing machine storage. There is no pending bottle delivery to replay automatically at login: open the cask and withdraw the retained lane again. If the discovery already committed, its batch key makes the retry idempotent.

Escrow remains durable and conservatively requires reconciliation for uncertain cross-system outcomes. If the item changes during database staging, nothing is removed or paid; its staged escrow is retired as restored. Confirmed payment rejection attempts restoration on the server thread, with its escrow transition on the worker.

The chunk/player/SQLite/economy boundaries are not a single crash-atomic transaction. This change does not claim to solve arbitrary forced-process-kill recovery or external economy uncertainty.

## Verification

- `mvn --batch-mode -f arcane-economy-api/pom.xml clean install`: 3 API tests passed.
- `mvn --batch-mode clean verify`: 499 Brewery tests passed, zero failures/errors/skips.
- 22 new regression tests cover failed-save retention, duplicate-click prevention, closed-session delivery suppression, rejected scheduling, immutable harvest checkpoints, SQLite restart/idempotency, escrow thread confinement, rejected-payment restoration, moved-item rejection, exact bottle matching, cask wood detection, best-process selection, V5-to-V6 recovery and bounded name caching/invalidation.
- Existing migration tests now cover V6 while retaining checks for V1–V5 checksums, rollback, future-version rejection and permanent-name preservation.
- `python3 scripts/verify-economy-packaging.py`: passed; all six API classes remain external to Brewery.
- Live Paper, protection-plugin integration, forced process termination and load testing were not run. Existing Paper smoke CI requires owner-provided EULA acceptance; this change does not set it.

## Owner pilot check

1. Back up the database, worlds and player data. Stop Paper, replace the Brewery JAR and restart.
2. Confirm storage reports schema V6 and the pre-upgrade backup exists.
3. Brew and withdraw a mature batch. Confirm the discovery is saved before the bottle is delivered.
4. Close a cask while a harvest is saving, then reopen it and retry. Receive one bottle with the original harvest conditions.
5. Test twelve matching wood components alongside a stray different wood; test mixed oak/dark oak below twelve each.
6. Improve a known formulation and check that the guide shows the improved process.
7. If sales are enabled, hold an ordinary bottle with an upgraded sibling in an earlier slot. Sell only the selected ordinary bottle. Move or change the quoted bottle before confirmation and check rejection.
8. Verify normal bottle-name updates and the single permanent rename still work.

