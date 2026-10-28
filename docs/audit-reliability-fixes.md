# Standalone release and reliability fixes — 28 October 2026

Baseline: master `ea7800775eb48e5b21d738d886793ba8ba7cbba3`.

## What changed

| Area | Final behavior |
| --- | --- |
| Economy | Removed built-in selling, pricing commands, provider binding, payout/settlement code, economy permissions, market configuration and the external economy API module/dependency. Brewery never connects to ArcaneEconomy. |
| Commands | `/brew sell` and `/brew value` are absent from handling, help and completion. There is no Java sale entry point. |
| Historical records | Existing transaction/escrow tables and payloads stay intact. Admin-only `/brewmarket pending` and `/brewmarket tx` inspect them without payout, refund or automatic reconciliation. |
| Discovery recording | A valid mature batch stays in its saved cask lane until its discovery write succeeds. Failed saves retain the batch for another withdrawal attempt. |
| Retried harvests | The first harvest time and producer are saved on the retained source item. Retrying after a disconnect, closed menu or normal restart uses those same conditions. |
| Casks | Count exact wood species separately. A stray different wood cannot hide twelve valid components; oak/dark oak cannot combine; stone does not qualify. Two independently qualifying wood species are rejected as ambiguous. |
| Recipe instructions | Personal knowledge selects that player's highest-quality recorded production process, with a deterministic batch-key tie break. Original records, private evidence, names and historical grants remain stored. |
| Bottle presentation | Cached positive/negative name results, in-flight deduplication, at most 32 outstanding name lookups and per-tick event coalescing. A rename invalidates that producer's cached names. |
| Existing drinks | Legacy consumption/effects and metadata readers remain. Historical intrinsic-value fields are preserved for bottle compatibility but no longer shown as a price in lore or inspection. |

The two market-specific audit issues are resolved by removing their execution paths. The discovery, cask, best-process and name-query fixes remain from the audit change.

Research still uses XP levels and construction uses materials. The eight production/specialist machines continue to work without a currency provider. Server owners can choose their own shops and player-trading rules.

## Upgrade

Build Brewery directly with `mvn --batch-mode clean verify`. Deploy only the rebuilt Brewery JAR and restart Paper. CI no longer builds or uploads an economy API. Other plugins may still need an independently installed economy provider; this update does not uninstall or modify one.

Old `market:` settings in an owner's existing configuration are ignored. They cannot reactivate selling or provider access. Default configuration no longer advertises these settings.

SQLite V6 adds only the production-history index used for best-process lookup. V1–V5 migration definitions/checksums are unchanged. The existing storage provider creates a consistent pre-upgrade backup. Existing recipe names, permanent rename guards, production batches, discoveries, materials and historical transaction records remain.

Harvest checkpoints are additive metadata saved on original cask items through machine storage. After a failed save or interrupted delivery, open the cask and withdraw the retained lane again. A previously committed discovery uses the same batch key, preventing duplicate production registration. Retried harvests retain credit for the first withdrawing player.

The previous public Java `settleMarketSale` methods and the API module are intentionally removed. An external plugin that called those methods must stop doing so; ordinary brewing consumers do not need an economy API.

## Verification

- Final `mvn clean verify`: **464 tests passed**, zero failures/errors/skips. Standalone packaging verification passed. No economy API build is needed.
- Regression coverage includes failed-save retention, repeated clicks, closed-session delivery suppression, rejected scheduling, immutable checkpoints, SQLite restart/idempotency, exact wood detection, best-process retrieval, V5-to-V6 upgrade and bounded name caching/invalidation.
- Standalone checks assert removed commands/Java entry points, no provider dependency or sale permission, protected read-only historical diagnostics, obsolete enabled-market settings having no effect on brewing configuration, and compatible historical bottle metadata.
- Packaging scans every compiled class for the removed API, provider adapters and settlement paths, and checks the descriptor, default config and Maven dependencies.
- Historical market persistence/restart tests remain. Obsolete live selling/integration/API tests were removed with their features.
- Live Paper, claims-plugin interaction, load and forced-process-kill testing remain unperformed. Paper smoke CI requires owner-provided EULA acceptance; this change does not set it.

Chunk/player/SQLite transfers are not a single crash-atomic transaction. These fixes do not claim arbitrary hard-crash safety.

## Owner pilot checklist

1. Back up database, worlds and player data. Stop Paper, replace Brewery JAR and restart.
2. Confirm schema V6 and a pre-upgrade backup. No API build/install is necessary.
3. Verify selling/value commands are absent and old enabled-market settings cannot enable payouts.
4. Harvest a mature batch; confirm its discovery is saved before delivery.
5. Close a cask during saving, reopen and retry. Receive one bottle with the first harvest's conditions.
6. Check twelve matching wood components plus a stray different wood, and mixed oak/dark oak below twelve each.
7. Improve a known formulation and inspect its best-process instructions.
8. Check all research purchases, specialist processing, shift-click/full inventories, name updates and permanent rename.
9. If historical market records exist, inspect them with the read-only admin command and retain them for manual review.

