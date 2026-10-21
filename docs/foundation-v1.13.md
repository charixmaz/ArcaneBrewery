# Foundation V1.13: deterministic alchemy

V1.13 adds the first canonical fantasy-alchemy layer.

## Identity rule

Alchemy follows the existing ArcaneBrewery rule:

**RNG determines variation, not drink identity.**

The effect family is derived deterministically from exact recipe/process data.

Batch quality can change:
- magical potency;
- instability;
- dangerous-reaction severity.

It does not randomly turn the same recipe into unrelated spell families.

## Inputs

Alchemy uses:
- canonical ingredient ratios;
- ingredient magical affinity;
- ingredient toxicity;
- explicit alchemical additives;
- batch magical potency;
- contamination and stability.

Explicit alchemical additives are already part of `ProcessProfile` and therefore part of the drink fingerprint.

## Effect families

Initial effect families:
- NONE
- FIRE_RESISTANCE
- INVISIBILITY
- REGENERATION
- NIGHT_VISION
- LEVITATION
- TELEPORTATION
- HALLUCINATION
- CURSE
- WITHER

Built-in magical ingredients provide deterministic mappings. Blaze powder tends toward fire resistance, chorus fruit toward teleportation, amethyst toward invisibility, and wither skull toward withering.

## Potency and instability

`AlchemyEngine` calculates:
- weighted magical affinity;
- magical potency;
- instability;
- dangerous-reaction state.

High toxicity, contamination, unstable magical affinity and excessive additive load increase instability.

## Consumption

Finished canonical drinks persist their alchemy result. Drinking applies the stored effect family and potency.

Dangerous reactions add short harmful backlash. Presentation effects are not recipe identity and do not alter the stored fingerprint.

## Current boundary

V1.13 does not yet add a dedicated alchemy machine or GUI for inserting catalysts/additives. The process model already supports additives, and the runtime can produce effects from naturally magical ingredients immediately.

The next alchemy milestone should introduce a controlled player-facing catalyst/additive preparation step and machine interaction.


## V1.14 player-controlled preparation

V1.14 adds the first deliberate catalyst preparation step before aging.

A completed fermented base can be infused by right-clicking an Enchanting Table while holding the base in the main hand and a supported catalyst in the off-hand. The catalyst is consumed once and a versioned additive ID/dose is written to ArcaneBrewery metadata.

Initial catalysts include blaze powder, amethyst shard, glistering melon, glowstone dust, shulker shell, chorus fruit, netherite scrap and wither skeleton skull.

The prepared additive is carried through cask aging into the final ProcessProfile. Because alchemical additives are fingerprint inputs, choosing a catalyst changes drink identity deterministically.

V1.14 allows one catalyst per batch. A future milestone should replace the direct workstation interaction with a full multiblock alchemy machine and GUI with processing time, multiple catalyst slots and instability feedback.
