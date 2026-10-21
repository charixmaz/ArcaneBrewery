# Foundation V1.8: runtime discovery persistence

V1.8 connects finished canonical drinks to the V1.2 discovery model and V1.3 SQLite persistence.

## Plugin lifecycle

ArcaneBrewery now initializes a single SQLite storage provider during plugin enable at:

`plugins/ArcaneBrewery/arcanebrewery.db`

The storage provider runs schema migrations before gameplay listeners are registered. Storage is closed during plugin disable.

A storage initialization failure prevents ArcaneBrewery from enabling with a partially functioning discovery system.

## Harvest registration

Canonical V1.7 harvests produce a complete `ProcessProfile`. After the finished bottle is created, ArcaneBrewery submits:

- harvesting player UUID;
- complete process;
- completion timestamp

to `DiscoveryRegistrationService`.

Database uniqueness on the fingerprint remains the only authority for first discovery. There is no in-memory check-then-insert race.

If the insert wins, the player becomes the permanent original discoverer.

If the fingerprint already exists, the existing owner remains unchanged and the harvesting player is informed that the recipe was reproduced rather than newly discovered.

## Threading

SQLite/JDBC operations are blocking and must not execute on Paper's main thread.

`DiscoveryRuntime` accepts an executor. The Bukkit integration supplies an executor backed by Paper's asynchronous scheduler.

The completion callback is returned to the main server thread before any player messaging is performed.

World, inventory and Bukkit entity mutations remain on the main thread.

## Bottle identity

The finished bottle continues to carry its canonical `drink_fingerprint` and quality metadata immediately. Database registration does not alter recipe identity and does not block bottle creation.

Discovery data remains private by default under the V1.2 rules. The runtime does not automatically publish or share a newly discovered recipe.

## Failure handling

A persistence failure does not delete or duplicate the already-finished bottle. It is logged and the player receives a temporary discovery-storage failure message. Because the fingerprint remains on the item, later administrative recovery is possible without treating presentation metadata as identity.

## Next step

The next milestone should add player-facing recipe/discovery commands or GUI projections and then begin consumption/intoxication mechanics. Marketplace/economy remains later.
