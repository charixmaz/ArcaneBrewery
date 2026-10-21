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
