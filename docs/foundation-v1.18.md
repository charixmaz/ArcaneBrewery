# Foundation V1.18: internal drink valuation

V1.18 introduces ArcaneBrewery's canonical internal drink-value model.

This is not a Vault economy integration and does not deposit, withdraw or transfer currency.

## Value units

Finished canonical drinks receive an internal value measured in ArcaneBrewery value units.

Value is calculated from the completed batch, not from item lore or legacy star ratings.

The current model considers:
- final batch quality;
- purity, flavor and aroma;
- contamination penalty;
- drink strength;
- distillation passes;
- aging duration;
- alchemical additive load;
- final alchemy effect and potency;
- dangerous magical reaction premium.

The model is deterministic.

The same canonical process and same final batch metrics always produce the same value.

## Identity separation

Drink value is not part of DrinkFingerprint.

Two batches of the same recipe may have different values because quality and contamination are batch outcomes rather than identity.

Changing value logic in a later balance revision must therefore never create a new drink identity.

## Value breakdown

DrinkValue stores:
- total value units;
- quality contribution;
- process contribution;
- alchemy contribution;
- contamination penalty.

The breakdown makes future balancing and admin diagnostics possible without reverse-engineering one opaque number.

## Bottle metadata

Finished canonical bottle metadata is now format V4.

V4 stores:
- value model version;
- total value units;
- quality component;
- process component;
- alchemy component;
- contamination penalty.

Existing consumption metadata remains readable.

Existing V3 alchemy bottles remain readable for consumption/alchemy but do not invent a historical stored value.

Only V4+ bottles expose canonical stored value through FinalDrinkMetadata.readValue.

Malformed or incomplete value data fails closed.

## Player presentation

Canonical finished-bottle lore now includes:

Value: <units> units

This is informational only. It is not yet a shop price, account balance or guaranteed market price.

## Compatibility

Legacy star-based drinks are not assigned canonical V1.18 value.

V1.18 does not modify discovery ownership, recipe secrecy or fingerprints.

No Vault dependency has been introduced.

## Verification

Tests cover:
- deterministic valuation;
- quality increasing value;
- contamination reducing value;
- process complexity and aging contributing value;
- deterministic magical premiums;
- V4 metadata round-trip;
- V4 compatibility with existing consumption/alchemy readers;
- pre-V4 bottles returning no stored value;
- malformed V4 value metadata failing closed.

The next economy milestone can build configurable market pricing, sell/buy boundaries and optional Vault integration on top of these stored value units without coupling the core simulation to an economy plugin.
