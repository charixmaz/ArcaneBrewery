# Foundation V1.6: fermentation integration

V1.6 connects canonical V1.5 distillery outputs to a real timestamp-driven fermentation stage.

## Flow

A potion carrying valid canonical distillery metadata can begin fermentation when stored in a real Barrel inventory. The legacy `BreweryListener` is repurposed as the bridge and is registered again, but it only acts on canonical Arcane distillery outputs.

The active flow is:

distilled base -> fermentation batch state -> fermented process draft -> future cask aging

Legacy arbitrary ingredient counting no longer starts fermentation.

## Fermentation state

A fermentation batch contains:
- its own UUID;
- the canonical V1.5 `DistilleryProcessDraft`;
- yeast/catalyst;
- fermentation temperature;
- duration;
- sanitation;
- oxygen exposure;
- start timestamp;
- FERMENTING/COMPLETED state.

Progress derives only from timestamps. Items therefore survive player disconnects and server restarts without tick counters.

The initial balance defaults are development values, not final gameplay:
- catalyst: `arcanebrewery:wild_yeast`
- 22 C
- 60 seconds
- sanitation 85
- oxygen exposure 10

These values are centralized in `FermentationSettings.DEFAULT` and can later become machine/config inputs.

## Item metadata

Canonical fermentation metadata uses:
- `arcanebrewery:fermentation_format`
- `arcanebrewery:fermentation_batch_id`
- `arcanebrewery:fermentation_stage`
- `arcanebrewery:fermentation_state`

The payload is bounded, versioned and explicitly encoded. No Java/Bukkit object serialization is used for authoritative fermentation state.

The original distillery metadata remains on the potion so downstream cask migration can validate provenance.

## Compatibility boundary

Legacy `brew_stage` WORT metadata is left untouched for the current cask engine. It remains compatibility-only and does not define canonical process identity.

V1.6 does not calculate final drink fingerprints, discovery ownership, cask quality or aging. Those happen only after aging is integrated and the complete identity-defining process exists.

## Next step

V1.7 should migrate cask aging onto canonical fermented drafts, convert the completed chain into `ProcessProfile`, calculate real batch quality, and remove legacy star RNG as an authority.
