# Foundation V1.17: machine maintenance and balance configuration

V1.17 makes the V1.16 alchemy failure system administratively tunable and gives players an explicit way to maintain worn machines.

## Behavior-preserving defaults

A new `config.yml` is installed with defaults matching V1.16 exactly.

Configured values include:
- alchemy processing duration;
- stable/unstable/failure/critical thresholds;
- additive-load instability weight;
- catalyst-diversity instability weight;
- integrity-loss instability weight;
- contamination instability weight;
- critical explosion power;
- repair material;
- cleaning material;
- integrity restored per repair item;
- contamination removed per cleaning item.

Invalid configuration disables ArcaneBrewery during startup instead of silently substituting dangerous values.

## Player maintenance

Default maintenance materials:
- Iron Ingot repairs the machine;
- Sponge cleans the machine.

To service a valid alchemy crucible:
1. hold the configured maintenance material in the main hand;
2. sneak;
3. right-click the Enchanting Table core.

Maintenance is not allowed while an infusion is processing.

The machine snapshot is persisted first. The player item is consumed only after the machine update succeeds.

Repair increases integrity up to 100.

Cleaning reduces contamination down to 0.

Using a maintenance item when no improvement is possible does not consume it.

## Persistence

Machine integrity and contamination remain part of the ABAM V3 machine snapshot introduced by V1.16.

Maintenance updates the same atomic machine record as the GUI inventory and operation state.

Machine condition remains separate from `ProcessProfile` and `DrinkFingerprint`.

## Configuration boundary

Balance settings are loaded once during plugin startup into an immutable `AlchemyRuntimeSettings` value.

Runtime code does not repeatedly read YAML.

The alchemy domain uses `AlchemyBalanceProfile`, keeping Bukkit configuration APIs outside the pure calculation layer.

Existing V1.16 default behavior is preserved unless an administrator changes the new configuration values.

## Known persistence boundary

The machine update and player inventory save are separate Minecraft persistence domains.

The plugin writes the machine snapshot before consuming the player's maintenance item to favor item preservation over silent machine-state loss if the machine save fails.

Abrupt process termination between the machine save and player-data save can still theoretically duplicate or lose a maintenance item. This is the same filesystem-level limitation documented during V1.15 and is not presented as a durable cross-entity transaction.

## Verification focus

Tests cover:
- default balance compatibility;
- threshold ordering;
- configured threshold behavior;
- repair/clean bounds;
- default YAML values;
- valid admin overrides;
- invalid thresholds;
- invalid materials;
- duplicate maintenance-material rejection.

Live Paper validation should additionally confirm sneak-right-click handling, item consumption, messages, shared GUI refresh and restart persistence after servicing.
