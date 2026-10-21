# Foundation V1.11: canonical consumption and intoxication

V1.11 introduces the first canonical player-consumption system.

## Canonical drink consumption metadata

Finished V1.7+ drinks now store:
- `drink_strength`
- `drink_quality`
- `drink_contamination`
- `drink_magical_potency`

Strength is derived deterministically from the completed process through `DrinkStrengthCalculator`. Legacy `alcohol_stars` is not authoritative for consumption.

## Player state

Each player persists timestamped:
- intoxication;
- tolerance;
- poisoning;
- dependence;
- last state update.

Player state is stored in player PersistentDataContainer for this milestone.

Wall-clock recovery means leaving the server does not freeze sobriety.

## Model

`IntoxicationModel` is Bukkit-free and deterministic.

Drink strength increases intoxication. Tolerance reduces effective dose. Poor quality and contamination can increase harmful outcomes. Repeated heavy consumption can slowly build dependence.

Initial gameplay severity bands:
- sober;
- buzzed;
- drunk;
- heavily drunk;
- dangerous.

The Bukkit layer converts those model states into short nausea, slowness, weakness, mining-fatigue and poison effects.

These values are gameplay balance values, not medical claims or a real blood-alcohol model.

## Recovery and longer-term effects

Intoxication and poisoning decay faster than tolerance/dependence.

Hangover risk and withdrawal risk are modeled explicitly, but V1.11 does not yet add a full persistent hangover/withdrawal event scheduler. That should be the next consumption milestone after live gameplay validation.

## Security and authority

Only canonical finished bottles with valid ArcaneBrewery consumption metadata enter the new model.

Renaming lore or adding legacy star tags does not create a canonical alcoholic drink.
