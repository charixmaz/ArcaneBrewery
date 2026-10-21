# Foundation V1.10: discovery ownership controls

V1.10 exposes the mutation rules already defined by the V1.2 discovery domain.

## Commands

Owners can now use:

- `/brew rename <fingerprint> <new name>`
- `/brew visibility <fingerprint> <private|shared|public>`
- `/brew share <fingerprint> <player|uuid> full`
- `/brew share <fingerprint> <player|uuid> partial <field,field,...>`
- `/brew revoke <fingerprint> <player|uuid>`

Recipe fields use the V1.2 `ProcessField` names, for example:
- `ingredient_ratios`
- `fermentation_catalyst`
- `distillation_cuts`
- `aging_wood`

Player names resolve only when that player is online. UUIDs can be used for offline recipients. This avoids hidden blocking profile/UUID lookups on the server thread.

## Ownership

The command layer does not implement ownership rules itself.

All mutations pass through `PlayerDiscovery`, which enforces:
- original discoverer ownership;
- one-time rename;
- no ownership transfer;
- private/shared/public semantics;
- full and partial recipe grants;
- revocation rules.

## Persistence and concurrency

`DiscoveryMutationRuntime` performs storage access asynchronously.

Each mutation:
1. loads the latest persisted discovery and revision;
2. applies the domain mutation;
3. saves with the expected revision;
4. retries once after an optimistic-lock collision using a fresh aggregate.

The retry does not bypass one-time rename or owner rules because the mutation is applied again to the newly loaded authoritative aggregate.

## Security

Partial sharing uses `RecipeDisclosure`. A partial share cannot accidentally become a full recipe because the domain rejects disclosure of every identity field.

The Bukkit command layer never edits JDBC rows directly.

## Scope

This milestone completes the first usable player recipe-knowledge loop:
discover -> inspect -> rename -> control visibility -> share -> revoke.

The next major gameplay system should be consumption and intoxication, using the canonical final drink metadata rather than legacy star ratings as authority.
