# Foundation V1.7: canonical cask aging and deterministic quality

V1.7 moves canonical fermented batches onto the V1 simulation at the physical aging cask boundary.

## Canonical aging

When a completed V1.6 fermented bottle enters an aging cask, ArcaneBrewery records:
- an aging batch UUID;
- physical casing wood;
- start timestamp;
- oxidation;
- storage temperature;
- cask cleanliness.

Canonical metadata uses:
- `arcanebrewery:aging_format`
- `arcanebrewery:aging_batch_id`
- `arcanebrewery:aging_wood`
- `arcanebrewery:aging_started_at`
- `arcanebrewery:aging_oxidation`
- `arcanebrewery:aging_storage_temperature`
- `arcanebrewery:aging_cleanliness`

Aging duration is measured from timestamps. Exact duration and wood are identity-defining process inputs.

## Final process and quality

At harvest the cask assembles the complete `ProcessProfile` from:
- V1.5 ingredient ratios and distillation controls;
- V1.6 fermentation profile;
- physical V1.7 aging conditions.

The final `DrinkFingerprint` is produced only from the completed process.

Quality is calculated by `BatchQualityCalculator`. Batch variation is seeded from the aging batch UUID, making it stable/reproducible for that batch while remaining independent of recipe identity.

Contamination and hygiene affect batch quality but do not change the fingerprint.

Final canonical bottles carry:
- `arcanebrewery:drink_format`
- `arcanebrewery:drink_fingerprint`
- `arcanebrewery:drink_quality`
- `arcanebrewery:drink_contamination`
- `arcanebrewery:drink_stage=FINISHED`

The old `alcohol_stars` field is still written as a deterministic compatibility projection from the 0..100 quality score because hygiene/sanitizer legacy gameplay still reads it. Stars are no longer randomly rolled for canonical drinks.

## Legacy compatibility

Legacy noncanonical bottles still fall back to `CaskQualityEngine` and `BreweryData` so existing worlds are not broken immediately.

For canonical batches:
- the random global star roll is not used;
- the pity counter is not used;
- fixed `BreweryData` recipe names/wood targets are not authoritative;
- cask UI uses the canonical aging timer instead of the old 15-second peak/spoil window.

The existing physical cask structure, signs, hygiene mechanics, methanol purge, contamination state, inventory layout and legacy inventory serialization remain in place.

## Initial balance defaults

V1.7 uses centralized development defaults:
- target fermentation: 22 C / 60 s;
- target aging: 120 s;
- target storage temperature: 18 C;
- temperature tolerance: 12 C.

Wood characteristics are centralized in `AgingBalance`. These values are game-balance placeholders and will later move behind configuration.

## Next integration

The next milestone should wire finished canonical drinks into V1.2/V1.3 discovery persistence, making the harvesting player the candidate discoverer while database uniqueness decides the original discoverer. After that, consumption/intoxication and economy can build on persisted canonical drink identity.
