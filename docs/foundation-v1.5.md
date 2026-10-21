# Foundation V1.5: distillery integration

V1.5 moves the active Brewing Stand distillery onto the V1 ingredient/process architecture while preserving the existing GUI shell and the current cask compatibility bridge.

## Authority

The active distillery now derives ingredient identity, ratios and purity through the V1.4 `MachineIngredientExtractor`. A `DistilleryProcessDraft` is authoritative for distillation-stage process data. Legacy `brew_stage` / `WORT_*` values are written only for the old cask path and must not be used to override canonical Arcane metadata.

Premium grain therefore remains `arcanebrewery:premium_grain`, not wheat plus a presentation flag.

## Process draft and controls

A draft contains:
- canonical ingredient ratios;
- per-ingredient purity transferred from V1.4;
- preparation method;
- temperature;
- heads/hearts/tails cuts;
- distillation passes.

It is intentionally not a completed `ProcessProfile`: fermentation and aging have not happened yet. Default controls are currently 78 C, 10/80/10 cuts and one pass. The model is ready for player-facing controls later without changing identity semantics.

## Machine identity and lifecycle

A machine is identified by world UUID and block x/y/z, never by Java `Block` object identity. A versioned `DistilleryBatchState` stores:
- batch UUID;
- stable machine location;
- process draft;
- start timestamp;
- duration;
- PROCESSING/COMPLETED state.

The state is stored on the Brewing Stand TileState under `arcanebrewery:distillery_batch_state`. Progress derives from timestamps, so reopening after elapsed time can complete a batch without requiring the GUI to have stayed open. In-memory tasks are only live visual/scheduling helpers and are keyed by stable machine location.

Completion checks the persisted batch id/state and is idempotent within the plugin lifecycle, preventing duplicate live tasks and repeated completion on GUI reopen.

## Canonical output metadata

Distillery potion outputs receive:
- `arcanebrewery:distillery_format`
- `arcanebrewery:distillery_batch_id`
- `arcanebrewery:distillery_stage`
- `arcanebrewery:distillery_state`

The state payload uses a bounded, explicit binary codec encoded as Base64 in PDC. It is not Java object serialization and contains the draft needed by downstream fermentation/cask migration.

Player lore is presentation only and intentionally does not reveal a full secret recipe.

## Legacy bridge

The old cask code still expects `arcanebrewery:brew_stage` and legacy WORT values. `LegacyWortBridge` derives a temporary compatibility stage from the canonical dominant ingredient. It is not identity and may map otherwise distinct drafts to the same legacy string.

The old numeric `ingredient_purity` output field is also written temporarily for downstream compatibility. New distillery code obtains source purity only through the V1.4 ingredient adapter.

The dormant `DistilleryProcessTask` remains legacy-only and is not registered by the plugin. Its conversion table is not authoritative. It should be removed once no external legacy caller depends on it.

## Inventory persistence

The existing GUI inventory still uses the historical Bukkit object stream format for item storage because replacing item serialization safely is separate from process-state authority. V1.5 bounds legacy reads to 1 MiB and fails closed on malformed payloads. New authoritative batch/process state never uses Bukkit/Java object serialization.

A future machine persistence milestone can replace the legacy item-container encoding after Paper item serialization compatibility is tested live.

## Integration boundary

V1.5 does not register discoveries. A distilled base is not a finished drink. Discovery remains deferred until all identity-defining process stages are complete.

V1.6 should integrate fermentation using the canonical distillery draft metadata. V1.7 should migrate cask aging/quality and finally remove WORT/star-RNG authority.
