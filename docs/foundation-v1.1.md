# Foundation V1.1: brewing simulation core

The simulation is pure Java under `domain`. No Bukkit events or machines call it yet.
Legacy gameplay is unchanged. No database, services, GUI or intoxication integration is included.

## Identity

`ProcessProfile` combines ingredient ratios, preparation, fermentation, distillation,
aging and additive/catalyst doses. Ingredient IDs are strings, including custom namespaced IDs.
Maps are copied, normalized and sorted. Text uses root-locale lowercase, trimmed and
collapsed whitespace. Ingredient amounts normalize proportionally; additive amounts
are absolute recipe doses. Zero amounts are omitted. Duplicate normalized IDs are rejected.

`DrinkFingerprint.from(process)` hashes a versioned, length-prefixed binary encoding
with SHA-256. It includes yeast/catalyst, process temperatures and durations, wood,
cuts, passes and additives. No decimal display rounding is applied to process parameters.
Sanitation, oxygen exposure, cask cleanliness, oxidation and storage temperature are
batch conditions and excluded from identity. Purity, composition lots, contamination,
quality targets, wood characteristics and variation are likewise excluded.

This replaces the previous fingerprint encoding. There are no production callers or
persisted fingerprint adapters on this branch; future persistence needs an explicit
fingerprint version/migration policy.

## Quality

`BatchQualityCalculator.calculate(process, targets, conditions, variation)` returns
the fingerprint, the existing six-axis `BatchQuality`, and explicit `Contamination`.
Targets describe recipe-specific fermentation temperature/duration and aging/storage optima.
Conditions supply an exact set of ingredient lots (composition and purity), incoming
contamination, catalyst stability and wood suitability/extraction/preservation.
Percentages and scores use 0..100. Durations use seconds, temperatures use Celsius.
Non-finite values are rejected, finite percentages bounded and negative durations rejected.

Composition is weighted by recipe ratios. Fermentation deviations reduce performance.
Sanitation, oxygen, incoming burden and dirty/oxidizing casks derive contamination;
ingredient toxins and inadequate discarded heads/tails derive toxicity. Both cause
quality penalties. Distillation passes have diminishing purification returns and
flavor loss. Zero passes skips distillation penalties. Maturation benefits suitable
wood up to the target duration; excess aging, oxidation and poor storage reduce quality.
Zero target aging means the recipe is unsuitable for aging.

`Contamination` retains separate biological burden and toxicity. States are clean
(burden below 5), minor (5), significant (30), dangerous (60), spoiled/ruined (80),
or toxic (toxicity at least 50, taking display precedence). These are deterministic
game balance thresholds, not real-world food safety claims.

`BatchVariation.NONE` gives baseline results. `BatchVariation.fromSeed(seed)` uses
a local seeded generator to create immutable offsets within +/-2 for quality, potency
and flavor. It never changes identity, contamination or toxicity and never rolls rarity/stars.
The caller owns the seed; calculations never read wall-clock time or global RNG state.
Weights and thresholds are initial balancing rules and should be tuned before gameplay integration.

## Later integration conflicts

- `CaskQualityEngine` uses a global RNG, star rarity rolls, pity counters, strict wood
  matching and a narrow aging time window. Its output must be replaced deliberately,
  rather than combined with the simulation quality result.
- `AgingCaskEngine` calls that engine and mutates batch counters/item metadata.
- `BreweryData.RecipeProfile` uses legacy recipe keys, wood requirements and durations;
  it needs an explicit adapter to canonical processes and recipe-specific quality targets.
- `IngredientPurityEngine`, `CaskHygieneEngine`, `DistilleryProcessTask` and
  `MethanolProducerEngine` contain existing purity, hygiene, distillation and toxicity
  behavior. Future adapters must choose one source of truth to avoid double penalties.
- `BrewBatch` still stores its own ingredient ratios. Future batch persistence should
  reference the canonical process and retain measured conditions and variation seed separately.

No legacy engine was removed or connected to the new calculator.
