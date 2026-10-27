# Ingredient relevance — effect generation 2

Newly started modern extractions capture generation 2. The catalyst still selects
the effect family, but actual ingredient proportions now determine how well the
base supports that family, its duration, profile stability and mild drawback
duration. This update adds no tiers, machines, materials, research costs or schema
migrations. Existing specialist caps and one-pass processing limits remain.

## Player behavior

| Base | Broad role | Tradeoff |
|---|---|---|
| Wheat | Reliable beginner base | Modest family support; cannot reach starter tier II merely by swapping catalysts |
| Potato | Mining strength; some combat/protection support | Short duration and reduced profile stability |
| Sugar Cane | Duration and travel | Reduced mining strength, profile stability and longer mild drawbacks |
| Apple | Recovery | Poor mining/combat suitability |
| Golden Apple | Protection and recovery | Does not replace a dedicated mining/duration base |
| Honeycomb | Profile stability and shorter mild drawbacks | Dilutes unsupported strength; does not remove drawbacks for free |
| Sweet Berries | Vision and concealment | Less stable, weak mining suitability |
| Exclusive Brewer's Grain | Resilient mining/protection | Neither maximum mining affinity nor maximum duration |

Unlocked ingredient Codex entries explain these broad roles. Locked riddles do
not reveal them. `/brew guide` also explains that a catalyst cannot erase the
base's limitations. `/brew inspect` continues to show actual stored effects and
durations, so players can compare results without ideal maturation spoilers.

At excellent workmanship with one Iron Nugget and pristine captured equipment:

| Base | Haste result | Hunger |
|---|---|---|
| Wheat | I / 25s | I / 15s |
| Potato | II / 19s | I / 17s |
| Sugar Cane | I / 41s | I / 18s |
| 1 Potato : 1 Sugar Cane | II / 26s | I / 17s |
| 1 Potato : 3 Sugar Cane | I / 38s | I / 18s |

These examples hold quality at 100 to isolate ingredient influence. Real batches
still need appropriate purity, culture, settings, equipment and harvest timing;
different bases can differ in achievable workmanship too. They are not guaranteed
results from arbitrary settings. No Haste III/IV/V or six-minute Haste III starter
profile has been added.

## Deterministic balance

`IngredientAffinities` defines explicit profiles for the eight obtainable bases.
Each base has per-family suitability, a duration multiplier, a profile-stability
offset and a mild-drawback multiplier. Ratios blend these linearly, in sorted ID
order. The canonical normalized process proportions are used: scaled quantities,
split stacks and reversed slot order do not create different benefits.

The existing quality execution factor remains `0.35 + 0.65 × quality/100`.
For each selected family, generation 2 additionally multiplies duration by:

`weighted base duration × (0.4 + 0.6 × weighted family suitability)`.

Infused tier II requires family suitability of at least 0.75 and either quality
at least 90 with supported power at least 0.8, or quality at least 80 with
supported power at least 1.5. Supported power is reagent power times suitability.
Natural uninfused benefits stay tier I. Existing tier and potency duration costs,
reagent consumption and family caps apply afterward. Poor affinity cannot be
overridden by stacking potency or duplicate catalysts alone.

Profile stability blends the base offset with captured equipment/reagent
stability, then clamps to 0–100. It affects the stored profile's caution/danger
classification; it does **not** repair hardware or rewrite the infuser's separate
pre-start failure/wear assessment. Honeycomb shortens the existing mild Hunger or
Slowness duration according to its share, with a one-second minimum. It does not
delete deliberate dangerous transformations, cure saturation or grant free
purification. Reaction conflicts, zero-quality gating and dangerous equipment
remain enforced. No contamination mechanics are reintroduced.

Different primary families in the same bottle evaluate their own suitability.
Adding a catalyst continues to replace the innate family selection, avoiding free
extra effects. Base influence now remains active after that selection. Bases with
no explicit obtainable profile use conservative fallback support; custom or old
unsupported material IDs do not become universally powerful.

No random seed/player/batch UUID participates. Formulation identity and names
are unchanged; only actual ingredient ratios already belonging to identity vary.
The exact rules and definitions are code-defined game balance, not chemistry.

## Compatibility

- Existing bottled profiles remain byte-compatible format 1 and are executed as
  stored. Consumption and specialist processing never recalculate ingredient
  influence on an old bottle.
- Batch policy generation is separate from stored profile format. Old generation
  1 output calculations remain frozen. Unmarked legacy output stays legacy.
- Extraction envelopes support absent legacy, `effects-v1:` and `effects-v2:`
  prefixes. New extraction starts use 2; old active extractions retain 0/1.
- Bases already being cultivated, infused or matured retain their 1/2 marker.
  Starting an infusion preserves that captured marker. An unmarked old canonical
  base newly entering infusion adopts 2, as a newly opted-in operation.
- Unknown/invalid generation markers fail closed. Existing ingredient metadata,
  schema V5, discovery identity, rename state, private evidence, server-firsts,
  hidden maturity targets and early poison/over-aging rules are unchanged.

Use **fresh extractions** to compare the new system. Do not compare an old
generation-1 stock bottle against a new batch and expect identical balance.

## Verification and deployment

Regression coverage retains explicit generation-1 expectations and adds fresh
ingredient comparisons, proportions, blended support, family specialization,
drawback/stability tradeoffs, dose/modifier limits, scaled/reordered equivalence,
multi-effect families, zero-quality/conflict gates, captured generation/codec
compatibility and all eight-base pairs against the current raw effect catalog
and unchanged specialist caps.

Local verification for this change: unchanged baseline 447 Brewery tests;
updated `mvn clean verify` 463 tests, zero failures/errors/skips; standalone API
baseline three tests; canonical JAR CRC/class-content and optional API packaging
checks; packaged SQLite schema-V5 startup. Full Paper startup and live gameplay
were not run; no EULA was accepted during this work.

Deploy: pull, rebuild, replace only the Brewery JAR and restart Paper. No economy
API source changes or rebuild is required on an already configured build system.
No database/world reset. Back up normal plugin/world/player data before updating.

Live checklist: brew fresh Wheat, Potato and Sugar Cane batches with the same
mining catalyst; compare actual effects with `/brew inspect`; try equal and
sugar-heavy Potato/Cane ratios; compare a Honeycomb blend's stability and mild
drawbacks; repeat the same proportions after restart; confirm an old bottle still
has its recorded effects. Startup/gameplay and server load require separate live
verification and owner-provided EULA acceptance.
