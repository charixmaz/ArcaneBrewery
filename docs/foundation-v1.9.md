# Foundation V1.9: player-facing discovery commands

V1.9 exposes the first safe player-facing view of persisted discoveries.

## Commands

`/brew discoveries [page]`

Lists recipes originally discovered by the executing player. The list is projected to `OwnedDiscoveryView` before leaving the application layer and contains only:
- display name;
- fingerprint;
- discovery timestamp;
- visibility;
- rename status.

It does not expose the stored `ProcessProfile` or recipe grants.

`/brew recipe <fingerprint>`

Loads `RecipeKnowledge` for the executing player.

The output respects V1.2 knowledge boundaries:
- UNKNOWN: no recipe metadata;
- IDENTIFIED: safe discovery metadata only;
- PARTIAL: only explicitly disclosed process fields;
- FULL: exact process details required for reproduction.

Owners naturally receive FULL knowledge through the domain model.

## Threading

All JDBC-backed discovery queries run through `DiscoveryQueryRuntime` on the asynchronous executor already used by V1.8.

Bukkit player lookup and messaging are returned to the server thread.

## Security boundary

The Bukkit command layer never receives `StoredDiscovery` for recipe inspection. Owner listings are projected before returning from the async application service, and recipe inspection uses `RecipeKnowledge`.

This preserves the rule that secret recipe data is not exposed merely because it exists in storage.

## Current scope

V1.9 is intentionally read-only.

Rename, sharing, revocation and visibility changes already exist in the V1.2 domain but are not exposed through commands yet. They should be added as a separate mutation milestone with optimistic-lock handling and explicit recipient resolution.

## CI

The Build workflow now also declares `workflow_dispatch` so a branch head can be manually validated if GitHub does not emit a push-triggered run for connector-created commits.
