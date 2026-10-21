# Foundation V1.2: player discovery and recipe knowledge

The new `domain.discovery` package is pure Java. It has no machine, Bukkit/Paper,
command, GUI, database, marketplace or economy integration. Legacy gameplay and
the V1.1 fingerprint/simulation implementation remain unchanged.

## Discovery and identity

`PlayerDiscovery.discover(discovererId, process, discoveredAt)` creates an immutable,
PRIVATE discovery. The caller supplies an explicit UUID and timestamp; domain code
does not read clocks or global player state. The aggregate records the original
discoverer, canonical `ProcessProfile`, derived `DrinkFingerprint`, discovery time,
generated temporary name, optional canonical name and visibility. There is no
ownership-transfer operation and the constructor is private.

`rename(ownerId, name)` succeeds once. A canonical name's presence records that
the one-time rename was used. Renaming preserves the original discoverer, timestamp,
temporary name, process and fingerprint. Every later access/visibility transition
preserves the name and rename flag. Names are presentation metadata, never keys.
No global uniqueness is implied by either temporary or canonical names.

`DrinkName` accepts 1..80 Unicode code points after whitespace normalization.
Blank names, controls (including tabs/newlines), Unicode formatting and line/paragraph
separators, and unpaired surrogates are rejected. Presentation capitalization is kept.
Ordinary Unicode whitespace is collapsed. Invalid renames do not consume the rename.

`DescriptiveNameGenerator` combines dominant ingredient, preparation method, aging
wood, distilled/undistilled style and additive presence. Ingredient ties break by
canonical ID; namespaces are retained in readable labels. Descriptor lengths are
bounded, including custom/Unicode IDs. The generator has no Minecraft recipe list,
randomness or player dependency. Multiple distinct recipes may have the same broad
description. Batch conditions and quality are not name inputs.

## Knowledge and selective disclosure

`knowledgeFor(playerId)` returns a recipient-specific sealed `RecipeKnowledge`:

| State | Payload |
| --- | --- |
| UNKNOWN | Recipient UUID only; no discovery metadata or process |
| IDENTIFIED | Recipient UUID and discovery metadata; no process |
| PARTIAL | Metadata and selected copied process details; no full process |
| FULL | Metadata and the exact immutable canonical process |

`DiscoverySummary` contains original discoverer, fingerprint, timestamp, display
name and visibility. It has no process or access grants.

`RecipeDisclosure` contains an immutable map from `ProcessField` to typed scalar,
amount-map or cut values. It never stores a reference to the full process or to
fermentation/distillation/aging profile objects. Selection includes ingredient ratios,
preparation, fermentation catalyst/temperature/duration, cuts/temperature/passes,
aging wood/duration and additive doses. Batch conditions are not partial recipe fields.
Empty selections and selections of every recipe field are rejected; use full sharing
for a complete recipe. Nested amount maps and caller selections are defensively copied.
`RecipeKnowledge.Full` also verifies that its process reproduces the summary fingerprint.

## Sharing, revocation and visibility

All changes require the original discoverer's UUID:

- `shareFull(owner, recipient)` grants the exact process.
- `sharePartial(owner, recipient, fields)` grants only the selected details.
- Each sharing call replaces that recipient's previous grant. Partial calls do not
  silently accumulate fields, and a partial call can downgrade an existing full grant.
- `revoke(owner, recipient)` removes a grant. Revoking a missing grant is harmless.
- Owner knowledge is always FULL; self-sharing and revoking owner knowledge are rejected.
  Recipients cannot re-share, rename, change visibility or take ownership through this API.

PRIVATE defaults to owner-only knowledge. Sharing switches PRIVATE to SHARED.
SHARED reveals knowledge only to the owner and explicit recipients; other players stay
UNKNOWN. Removing the last SHARED grant returns the discovery to PRIVATE.
PUBLIC makes discovery metadata IDENTIFIED for everyone, without granting a recipe or
changing ownership. Explicit grants still apply. Revoking a public grant falls back
to IDENTIFIED. Setting PRIVATE explicitly clears all grants. Setting SHARED requires
at least one recipient.

## Reproduction

`ReproductionVerifier.reproduces(discovery, candidate)` compares the discovery's
fingerprint with `DrinkFingerprint.from(candidate)`. It does not compare names,
owners, quality, variation seeds, or the complete process record by equality.
This preserves V1.1's distinction between recipe identity and batch conditions.

## Integration boundaries and design concerns

- The aggregate contains secrets for trusted application code. Only distribute
  `knowledgeFor` projections to players; never serialize a `PlayerDiscovery`
  directly as a player-facing response. Caller UUIDs must come from authenticated
  application context rather than untrusted player-supplied IDs.
- Every change returns a new immutable snapshot. Future persistence must atomically
  replace the current snapshot, using locking/version checks to prevent stale updates
  from bypassing the one-time rename or restoring revoked grants.
- Revocation removes future access, not a player's memory, screenshots or previously
  received immutable snapshots. A player may manually combine information learned
  across time. Secrecy is an access policy, not information erasure.
- No registry resolves simultaneous discoveries of the same fingerprint. Global
  first-discoverer arbitration, deduplication, persistence and name moderation are
  future application responsibilities. Ownership of an existing aggregate cannot change.
- Full shares currently preserve all V1.1 process fields, including recorded batch
  conditions; partial shares expose only selected recipe identity fields. A later
  recipe/batch split should retain fingerprint compatibility.
- Visibility, naming and grants have no current legacy gameplay consumers. Future
  machine/command integration must deliberately use these projections rather than
  legacy recipe keys, star rarity or lore as identity.

## Verification

Tests cover ownership/private defaults, deterministic/custom ingredient names,
single-use rename and validation, exact full sharing, field-level partial disclosure,
immutable maps, access authorization, revocation, visibility transitions, and
fingerprint-only reproduction across different batch conditions and seeded variation.
The build uses Java 25 and `mvn clean verify`. On this Windows workspace an inherited
delete-deny ACL prevents cleaning an existing `target`; verification can run from a
fresh temporary copy with byte-identical `pom.xml` and `src` files.
