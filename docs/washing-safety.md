# Washing safety fixes

Ingredient washing still handles one held item per cycle, adds 25 purity points
up to 100, and consumes one cauldron water level on success.

## Inventory delivery

The washer plans the complete storage-inventory change on clones before changing
items or water. A single held item is replaced in its existing slot, so a full
inventory does not prevent its wash. A held stack leaves its unwashed remainder
in hand; the washed item must fit a matching partial stack or another storage
slot. Different purity/metadata is not treated as stack-compatible space.

If delivery cannot fit, the player sees a short make-room message. No ingredient
or water is consumed, and no washed item is dropped or discarded. Normal item
metadata is retained. Offhand callbacks cannot duplicate the cycle. On an API
update exception, the handler attempts to restore both inventory and cauldron
data and propagates the exception for server logging; this is not a claim of
cross-world/player-data crash-atomic persistence.

## Interaction permissions

The HIGHEST-priority listener ignores cancelled events and explicitly respects
both `useInteractedBlock() == DENY` and `useItemInHand() == DENY` before doing any
work. DEFAULT/ALLOW interactions remain eligible. After accepting a supported
wash, vanilla block/item behavior is denied so the ingredient is not also eaten
or used by vanilla processing. Display-only control/reference items are excluded.

Paper exposes independent block and item decisions; its deprecated aggregate
cancellation flag is not sufficient to cover both. See the [Paper API contract](https://jd.papermc.io/paper/26.1.2/org/bukkit/event/player/PlayerInteractEvent.html).

This fixes honoring denials already set when this handler runs. It does not
certify every third-party region plugin, permission configuration or later event
handler; live checks should use the pilot server's actual protection plugin.

## Regression checks

Tests exercise the actual listener with API-boundary doubles: full inventories,
single versus stacked ingredients, matching partial stacks, different purities,
exact quantity/water conservation, final water level, repeated washing to 100,
block-only/item-only/full cancellation, DEFAULT/ALLOW, offhand/non-right-click,
malformed/display-only items, unrelated metadata and API-failure rollback.

Deploy by replacing the rebuilt Brewery JAR and restarting Paper. No schema,
recipe, effect-profile or economy API changes. Live checks: attempt to wash a
stack with a full inventory (no consumption), wash a single held item with a full
inventory (same-slot success), wash into an existing matching partial stack, and
attempt washing where the actual protection plugin denies interaction.
