> Exploration presentation: ideal maturation times are now hidden from cask
> lanes, bottle lore, inspection, quality explanations and Codex walkthroughs,
> including old stored breakdown hints. Casks show elapsed time and a neutral AGING
> state without a countdown, ready colour or perfect-window warning. Bottles retain
> the actual harvest time so players can record and reproduce their own results.
> Hidden targets, quality rules, persistence and discovery identity are unchanged.
> Player-visible hints below about shown target times are historical and superseded.

> Screenshot regression fix: unfinished V2 batches now have zero final quality
> throughout the entire period before their captured maturity target. Poison remains
> specific to new-policy withdrawals in the first 60 seconds. After maturity, final
> workmanship is multiplied by maturation suitability (0–1); severe over-aging
> therefore ruins a batch instead of merely losing 10 percentage points. At 18 C,
> suitability reaches zero at `2 * target + 15` seconds. Ruined zero-quality batches
> do not register a discovery. Existing bottled statistics are not retroactively
> rewritten; retest with fresh withdrawals. Existing captured timing is preserved.
>
> Automatic factor-by-factor chat output is removed. Hold an elixir and use
> `/brew inspect` for a read-only GUI with summary, factors, effects and safety.
> Bottle lore is compact, with the italic producer byline retained. Discovery-save
> errors now log the full exception and batch ID to the server console, including
> when the player disconnects. A screenshot alone does not identify the failure.

> Maturation balance update: NEW V2 cask batches use a stable, formulation-derived
> pseudo-random target from 120–300 seconds, captured on insertion. The perfect
> window is target through target+15 seconds. Existing active batches retain their
> saved targets. The former fixed 120–135s reference below is historical: use the
> target shown by the cask for a new batch. Withdrawal at elapsed 0–60s inclusive
> gives zero quality, a poisonous unfinished product and no discovery; 61s until
> maturity remains unfinished without discovery. Poison is applied on consumption,
> never on inventory withdrawal. Unfinished products cannot be sold as finished
> canonical elixirs. Quality still depends on every other production condition.
>
> Stable successful infusion with pristine equipment no longer adds unavoidable
> contamination or a fixed dose-based workmanship penalty. Actual captured wear,
> dirt, instability/failure losses persist, including through subsequent infusion.
> Magical instability and potency remain separate from workmanship quality.

# Closed-beta brewing gameplay V2

These are fantasy game balance rules, not chemistry. The normal path remains Wash → Essence Extractor → Cultivation Barrel → optional Arcane Infuser → Maturation Cask. No economy/API changes or new required integrations are involved.

## Player controls

| Stage | Controls and default | Inputs and effects |
| --- | --- | --- |
| Washing | Existing water-cauldron cycles, +25 purity, capped at 100 | One supported item per water level; repeat to 100. The Codex lists the seven ordinary washable materials. |
| Extraction | Explicit START. 60–90 C, 2 C steps, default 78. Balanced cuts 10/80/10, Wide 2/96/2, Clean 15/70/15. One pass. | Bottle slots 1–3, one each. Ingredient slots 5–6, 1–8 items each. Every selected ingredient item is consumed per run, yielding one base per supplied bottle, maximum three. Ratios use actual quantities. Unsupported inputs stay retrievable. Wide increases strength/potency and carries toxicity; Clean produces milder output. Temperature and cuts affect the assessment and strength. |
| Cultivation | Put bases into an ordinary barrel, close, then **sneak-right-click**. Select culture, setpoint and duration, then START. | The built-in starter library needs no consumable culture item or external plugin. WILD: 22 C / 60 s; LUMINOUS: 28 C / 90 s; QUIET: 16 C / 120 s. Selecting a culture resets its ideal settings. Temperature cycles 10–36 C in 2 C steps; duration cycles 30–300 s in 30 s steps. Existing configured initial durations remain supported. Active bottle settings are locked. |
| Infusion | One dose in Catalyst I; optional second dose in Catalyst II; START. | One item consumed per selected slot. Two identical catalysts produce their combined stored dose. Existing retained larger stacks still consume one item per slot. Extra dose can increase instability. Existing failure, dangerous-reaction, wear and maintenance rules remain. Equipment integrity, contamination, reaction outcome and instability are captured on the surviving base. |
| Maturation | Physical wood choice; withdrawal time | Four lanes show wood, elapsed time, captured target and clean/contaminated status. New V2 batches begin sealed at 18 C, oxidation 0 and cleanliness 100. Existing cask contamination blocks normal operation until cleaned. Wood changes flavor; maturation and over-aging change workmanship. Early withdrawal remains unfinished and never registers a discovery. |

Processing operation times still use the existing validated extraction/cultivation/infusion/maturation configuration. The cultivation duration selection is the actual culture process duration. Already-started operations retain their stored timing and controls; selecting different controls does not rewrite earlier bottles. The UI selects fixed setpoints, not a physical heat simulation.

LUMINOUS has a 1.2 potency/exposure multiplier; QUIET has 0.75; WILD has 1.0. Wide extraction has a 1.2 magical-potency multiplier and Clean has 0.8; strength also uses the actual cuts and extraction temperature. These do not guarantee distinct magical effect types.

## Identity and exact process

There are two deliberately separate identities:

* `DrinkFingerprint.from(process)` is the unchanged V1 exact-process encoding. Old bottle decoding and V1 reproduction still use it. Existing default mature 30-second stages remain unchanged for old in-flight batches.
* `FormulationIdentity.from(process)` is the V2 discovery key. It includes normalized ingredient proportions, selected culture/style and catalyst IDs with doses. It excludes precise temperature, elapsed duration, extraction settings, wood, maturation time, purity, sanitation, condition, quality and random variation. Culture is the explicit finite variant selection, not a temperature band.

Thus 1 Wheat + 2 Apples and 2 Wheat + 4 Apples have the same formulation; changing their ratio or catalyst dose changes it. Changing 22 C to 22.1 C or harvesting later does not create a discovery. The three selectable cultures are distinct styles. Legacy unsupported culture identifiers are preserved, not guessed into a new style.

Finished bottles retain the old exact-process hash under `drink_fingerprint` and the discovery identity separately under `drink_formulation_v2`. Exact instructions are **not** embedded into newly traded finished bottles. The private database `production_batches` history keeps the exact process, completion time, quality, and a bounded native source-batch evidence payload. This evidence preserves ingredient purity, cultivation/extraction settings, aging state/target/seed and realized infusion conditions separately from the discovery key. Recipe access returns only authorized process projections, never another player's source evidence.

## Reproducible workmanship

Every score is bounded to 0–100. Weights sum to one:

| Factor | Weight | Rule |
| --- | --- | --- |
| Ingredient purity | 20% | Quantity-weighted measured purity. |
| Temperature suitability | 20% | `100 - 8 × abs(actual - culture ideal)` |
| Cultivation duration | 15% | `100 - 100 × abs(actual / culture ideal - 1)` |
| Extraction | 15% | `100 - 2×abs(T-78) - 3×max(0,10-heads) - 2×max(0,10-tails) - 0.5×abs(hearts-80)` |
| Sanitation/storage | 10% | `min(cultivation sanitation,cask cleanliness) - 0.5×oxygen - 0.5×oxidation` |
| Maturation | 10% | Perfect at target through target+15 s; below target lose `100×(1-actual/target)`, afterward lose `100×(actual-target-15)/target`; also subtract `2×abs(storage T-18)` |
| Catalyst stability | 5% | 100 without catalyst; otherwise the lesser of captured reaction/equipment stability and `100 - 5×total dose`. |
| Formulation suitability | 5% | `min(100, 2×substrate)`: WILD uses sugar+starch, LUMINOUS sugar, QUIET starch. These are weighted registry composition values. |

Separate contamination penalty is `0.5×incoming contamination + 0.5×weighted ingredient toxicity + 2×max(0,10-heads) + max(0,10-tails)`, bounded to 100. Incoming contamination includes cask cleanliness loss and captured infuser contamination/reaction gain. Final quality is weighted factor sum minus this penalty, bounded to 0–100. Surviving failed infusions retain realized contamination; cleaning equipment afterward does not erase that batch's history.

Mature withdrawal prints each factor's actual score, the applicable target/rule and the actual contamination penalty. The breakdown is also stored on the bottle. Potency, contamination, stability, strength/exposure and economic value remain separate statistics. Seeded variation may alter sensory/potency characteristics, but does not add a workmanship roll or alter identity. Wood affects the sensory calculation. A mild recipe can have 100% quality; a powerful recipe can be poorly made.

### Achievable default 100% reference

1. Wash ordinary Wheat to 100% purity using repeated existing water-cauldron cycles.
2. Supply one Wheat and one Glass Bottle. Extract at 78 C, Balanced cuts; press START.
3. Insert the base in a plain barrel, close, sneak-click. Select WILD, 22 C and 60 seconds; press START and let it complete.
4. Skip optional infusion. Use a clean, functioning Oak cask. Mature the cultivated base for the default 120-second target and withdraw in the deterministic 120–135 second perfect window.
5. Withdraw while conditions remain clean. All eight factors score 100 and contamination penalty is zero: exact workmanship quality is 100. This is verified using the actual built-in Wheat composition and through AgingService with different variation seeds. QUIET Wheat at its ideal settings also reaches 100 with lower strength.

Withdrawal beyond that 15-second window can reduce workmanship without changing discovery. Altered server timing/unsafe conditions need an adjusted reference; unsafe processes are not promised perfection. These instructions do not seed records, grant items or reserve server-first recognition.

## Personal discoveries, learning, names and privacy

The personal key is `(formulation identity, player UUID)`. First independent successful mature completion unlocks that player's private Codex record. Other players can independently unlock and name the same formulation. Repetition updates best quality without creating duplicates or moving ownership.

The separate `server_firsts` unique key is assigned in the same immediate database transaction as independent production. Simultaneous writes have one permanent winner. Credit contains player UUID and time, never private instructions. The Codex shows recognition as a safe UUID label; it is not forced onto other producers' bottles.

Full or partial plugin sharing creates LEARNED provenance. Partial projections copy only the selected fields and never contain a ProcessProfile. Full projections contain one exact process, not another producer's unrelated batch notes. Producing a learned record retains LEARNED status. Revocation removes the granting player's current access; historical learning metadata remains. Independently produced exact process records are the producer's own knowledge and cannot be erased by another player. Instructions learned outside the plugin cannot be detected.

Personal names are editable, 1–80 visible characters, with whitespace normalized; blank/control/invisible/format-code names are rejected. `/brew rename held <name>` requires a finished bottle whose authoritative producer UUID is the acting player and whose formulation is a full personal record. `/brew rename <short-id> <name>` requires an independently produced or currently fully learned personal record. At least six unambiguous hex characters resolve across the entire relevant personal catalog, including learned records, not a first-200 slice. Ambiguity is an error. Visibility/share/revoke still accept full IDs.

Names do not change identity or another player's label. New V2 finished bottles record the bottling player as producer, with a cached name and UUID fallback. The byline is explicitly italic; statistics and name are explicitly non-italic except the bold name. Safe inventory interactions refresh identifiable bottles from that producer's personal label. Legacy/old bottles lacking reliable producer metadata remain unattributed; possession never assigns provenance. Unfinished products have no producer-branded discovery bottle identity.

Public means discoverable metadata, not a full-process grant. Permissions are rechecked at read/mutation time. Partial grants from different sources are not merged into an accidental full process. Onward sharing is an explicit grant from that player's record; revocation is not DRM or erasure of recipients' independent production.

## Storage upgrade and recovery

SQLite schema V3 is additive. Before upgrading a nonempty pre-V3 database, `VACUUM INTO` writes a consistent recoverable `.pre-gameplay-<UUID>.bak` beside it, including committed WAL pages. Backup failure stops startup. Existing migration checksums remain intact. DDL and the repeatable V2 data conversion each use transactions; the conversion marker is committed with all converted records. Failure rolls back rather than resetting data.

The original discovery and grant tables remain verbatim as historical archives. Existing exclusive owners become personal independent holders; recipients become learned holders. V1 fingerprints remain aliases. Exact legacy process/name/grant data remains readable through the original alias subject to current access restrictions. Same-player collapsed variants retain the earliest representative personal name/process, with every original variant/name preserved in the legacy archive and accessible alias, rather than overwritten or deleted. Different players retain separate names and process records.

Historical server-first selection sorts reliable Instant timestamps, then lexicographic owner UUID, then old fingerprint. This is deterministic on ties. An old alias never grants access merely because the requester knows a different collapsed batch. Unchanged migrated grants retain their original alias-specific selections; revocation removes current grants without destroying history. Shared exact process versions are frozen separately so subsequent production or renaming cannot silently redirect someone's recipe instructions.

New production history and best-quality records are separate from legacy archives. Historical best quality is shown as not recorded until an actual new production score is available; migration does not invent quality values. Old finished bottles and in-flight machine state codecs remain supported. New processing uses the `distillation-v2` preparation marker to select the explainable quality policy; old batches keep the earlier quality calculation and aging-stage rules. No reset of world, database, messages or inventories is required.

## Validation and live checks

Automated coverage includes formulation scaling/styles/doses, exact-vs-discovery identity, attainable perfect workmanship, measured penalties, captured state codec round trips, independent discoveries, atomic first assignment, learned/revoked provenance, partial privacy, large catalogs/ambiguity, producer authorization and tooltip styles, additive backup/migration/aliases, and existing inventory/session/automation regressions.

After deployment: test both ingredient slots and every extractor setting; explicit cultivation start/cultures; catalyst quantities and unsafe reactions; early/mature/late cask withdrawal; two players discovering/naming the same formulation; partial/full sharing and revocation; rename held and ambiguous IDs; restart/unload with active batches; owner/session/automation denial. Use the existing Paper smoke EULA gate only. Automated boundary tests do not replace live Paper gesture and native-item persistence testing. Minecraft world saves, player inventory files and SQLite commits are not one atomic filesystem transaction; if discovery persistence fails after bottling, the bottle remains with the player and an error is reported; private history may need administrator recovery.
