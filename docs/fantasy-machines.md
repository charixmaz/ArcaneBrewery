> Current standalone release: built-in selling and the economy API were removed.
> Build Brewery directly with `mvn clean verify`; see [current release notes](audit-reliability-fixes.md).
> The version-specific integration/build notes below describe the earlier release.

> Current release: [Phase 2 specialists](phase2-specialists-release.md) supersedes earlier sharing, repeatable naming, contamination and roadmap-only specialist descriptions below.

> Current controls, personal discovery/naming, and migration semantics are specified in [Brewing gameplay V2](brewing-gameplay-v2.md); those rules supersede the older behavior below.

# Fantasy defaults and persistent machines

Reviewed from master `dba94367068d9f95287198d8c0e26b31be3b7b02`.
Only ArcaneBrewery changes; ArcaneEconomy and `arcane-economy-api` are unchanged.

## Player experience

The default production path is **washing → Essence Extractor → Cultivation Barrel
→ optional Arcane Infuser → Maturation Cask → Finished Elixir**. Product description
is “Brewing & Alchemy”; the plugin name and `/brew` remain unchanged.

Elixirs now cause temporary **Arcane Saturation**, **Magical Overload** above the
safe threshold, and temporary **Arcane Recovery**. Contamination still causes
hazards; legitimate recipe alchemy reactions are applied on consumption, not on
periodic recovery. Default policy does not develop tolerance, dependence or
withdrawal. This changes mechanics rather than simply relabeling the old model.

`consumption.legacy-alcohol-mechanics: false` is the default. Explicitly setting it
to `true` restores the historical progression policy. Fantasy state uses separate
`player_arcane_*` PDC. On first use, only the old temporary exposure and poisoning
are imported; old dependence, tolerance, hangover and withdrawal do not become
fantasy symptoms. Original legacy saved fields are retained. Offline recovery uses
timestamps across logout/rejoin/restart. No blanket potion-effect removal occurs:
unrelated effects remain, and previously applied short symptoms expire naturally.
Malformed numeric saved state is safely bounded when read, not bulk-deleted.

Finished-elixir presentation is rebuilt from real canonical metadata: quality,
arcane load, magical potency, contamination, readable alchemy effect, instability
and calculated value when available. Lore does not expose a private recipe or
discoverer. Names, inventory quantity, fingerprint and unrelated PDC stay intact.
Player join, held-item changes and ordinary interactions refresh older bottles.
Paper's supported `HIDE_ADDITIONAL_TOOLTIP` flag hides the misleading native
“No Effects” row without adding duplicate potion effects.

## Messages and help

`messages.yml` migration version 2 updates only missing keys or **exact historical
stock defaults**. Custom server messages and extra keys survive. Re-running the
migration is idempotent; newer message schemas are refused without rewriting them.
`names.extractor`, `names.refined-base`, `names.cultivation`,
`names.cultivated-base`, `names.infuser`, `names.cask`, `names.elixir`,
`names.unfinished` and `names.cleanser` configure presentation where used by the
interfaces. Internal classes, serialized enums, legacy stage IDs, PDC keys and
database fields are not renamed for terminology.

`/brew help [getting-started|machines|recipes|sharing|commands]` uses concise topics
with tab completion. `/brew status` shows readable fantasy conditions by default.
Only `/brew recipe` accepts short IDs, resolving an unambiguous prefix of at least
six hex characters among the first 200 owned discoveries. Shared recipes and
rename/visibility/share/revoke commands still need full fingerprints. Ownership
and privacy commands remain unchanged. Help describes the implemented optional
economy; it does not advertise a Codex, tracking, trust or future functionality.

## Structures and ownership

| Machine | Valid structure / first eligible use |
| --- | --- |
| Essence Extractor | Brewing Stand directly above a Crafting Table; use either block |
| Cultivation Barrel | Plain Barrel with canonical refined/cultivating bases; first valid insertion or opening existing eligible contents |
| Arcane Infuser | Enchanting Table, Crying Obsidian directly below and at least one horizontal Amethyst Block |
| Maturation Cask | Barrel, nearby `[Brewery]` sign and at least 12 matching wood stairs/slabs/planks/logs in the 3×3×3 envelope |

The cask retains its existing scan order and legacy `BlockFace` sign offsets;
ordinary directly adjacent signs are recommended. Required components include the
recognized machine footprint and its activating sign. Ordinary vanilla barrels,
stands without the extractor structure and unrelated hoppers remain normal.

First valid use claims a core with a persistent machine UUID, owner UUID, kind and
cached owner name. **Chunk PDC** stores ownership, independently of whether the
actual core implements `TileState`. No remote name lookup occurs; the saved name
or UUID fallback is shown. Existing unclaimed worlds are adopted on eligible use;
no historical owner is invented. Existing native stand/barrel contents must be
emptied through their ordinary inventory before adopting the separate custom GUI.

Only the owner operates the four machines. `arcanebrewery.admin.bypass` (default
op) permits administrative access without changing the owner. Bypass never permits
simultaneous access or bypasses occupied-core protection. Machine-kind conversion
requires emptying and removing the old core before building the new machine.

One lease per persistent machine UUID governs views; actual inventory references
and holder types identify GUIs, never titles. Closing, disconnect, chunk unload,
shutdown or canceled opens release the intended lease. Competing access reports
`[ArcaneBrewery] This machine is already in use.` Closing a view does not reset a
process. Persistent ownership is separate from these temporary sessions.

Unauthorized players cannot break claimed cores or their required footprint.
Occupied/processing cores cannot be removed even through bypass. Claimed machine
components are protected from explosions and piston movement; unsupported or
corrupt records fail closed. Owner removal of an allowed support component pauses
runtime activity without completing or resetting a batch. Repair and reopen to
reconcile timestamp progress.

Automation has no accountable owner, so hopper insertion/extraction/pickup and
vanilla brewing are blocked for recognized Brewery machines, including virtual
control inventories. Anonymous canonical-base insertion into an ordinary barrel
is also blocked; claim it by normal player insertion first. This is intentionally
conservative and does not add trusted players or shared ownership.

## Inventory persistence and controls

Extractor and cask inputs now use a bounded, checksummed snapshot with native
Paper item bytes and captured process state. Saves compare the previous payload
and commit before changing a cursor or player source stack. Legacy serialized
inventories are imported through a bounded class/alias allowlist. Their original
payloads remain as backups; a committed-snapshot marker prevents replaying an old
backup if the new snapshot disappears. Invalid payloads or items in reserved
control slots are quarantined, never replaced with empty contents.

The extractor has one authoritative inventory per core, one bottle per bottle
slot and locked inputs during processing. It shows Bottle Inputs, Ingredient
Input, EMPTY/IDLE, PROCESSING, READY, progress and remaining time. Unsupported
ingredients can be retrieved safely. Infuser labels identify Base, Catalyst I,
optional Catalyst II and Start Infusion; progress uses its persisted operation.

Casks show four lanes with EMPTY/AGING/READY/CONTAMINATED state and elapsed/remaining
time. Both ordinary withdrawal and shift withdrawal finish a canonical lane;
early withdrawal creates an **Unfinished Elixir** and never registers a discovery.
Mature withdrawal attempts normal discovery registration. Decorative items never
become stored inputs or player loot. Indirect transfer gestures are canceled;
cask shift insertion/withdrawal is implemented explicitly. Full player inventories
receive safely dropped leftovers rather than silently losing the output.

Canonical refined bases must complete cultivation before insertion; they cannot
fall through to legacy random-star handling. Malformed/orphan canonical metadata
also never qualifies as legacy. Unsupported older inputs are retained without
inventing maturity statistics or recipe identities.

The existing Cleansing Solution behavior remains: apply it from the cursor to the
cask cleaning/ventilation control, then close and use the sign to reboot. Existing
cleaning thresholds and contamination fuse are retained. Contamination quarantines
contents instead of silently erasing the saved inventory. The legacy `[Producer]`
path retains its refined-catalyst input and ten-second timing. Its non-container
Gray Concrete core now uses the same durable chunk-backed inventory boundary and
exclusive view/gesture safeguards; no fifth ownership kind or new cleaning balance
was introduced. Cleansing Solution cannot be accidentally consumed as an elixir.

## Timing configuration

| Key | Default | Validation |
| --- | --- | --- |
| `processing.extraction-seconds` | 10 | 0.001–604800 seconds |
| `processing.cultivation-seconds` | 60 | Whole seconds, 1–604800 |
| Existing `alchemy.processing-seconds` | 8 | Existing positive duration, at most 3600 seconds |
| `processing.maturation-seconds` | 120 | Whole seconds, 1–604800 |

Non-numeric, non-finite, zero, negative and out-of-range settings fail startup with
an actionable configuration error; they are not silently substituted. Settings
are read on restart. Existing active batches retain their captured timing; new
batches use the new settings. Old aging metadata without the optional maturity
field retains **120 seconds**, independent of current configuration.

The default mature identity still advances in 30-second stages: the same process
withdrawn after 121s and 126s has the same identity. A configured maturity is
captured with a new batch and participates deliberately in its duration/identity
staging; changing configuration does not reinterpret old bottles. Catalyst versus
no catalyst remains identity-relevant. RNG still changes variation, not identity.
Extraction/infusion waiting times remain scheduling settings, not new fingerprint
fields; cultivation retains its existing exact fermentation-duration semantics.

## Verification, deployment and limits

Baseline: 295 Brewery tests passed; baseline GitHub Build run `40103806134` was
observed successful. No pre-existing test failure was found. This update verifies
the complete Brewery Maven suite, native-API compilation, snapshot/ownership/
session/automation boundaries, timing compatibility, fantasy policy, saved-state
compatibility, metadata-preserving presentation and message migration. See the
delivery report for the final count and pushed CI result.

Final local `mvn --batch-mode clean verify`: **342 tests, zero failures/errors/
skips**, including 47 additional tests over the baseline. Shell and PowerShell
helper syntax checks passed. Presentation tests also assert explicit non-italic
component styles and unchanged potion effects/PDC; washing regressions cover
rendered lore normalization so repeated washing cannot duplicate statistics.

Local clean builds use fresh source/POM copies because of inherited checkout
target deletion restrictions. SHA-256 comparisons check all source/resource/POM
inputs against the verified copy. Packaging verification uses the **already
installed, unchanged** API JAR and confirms no economy classes are bundled.

The Paper startup smoke test was **not run**: its acceptance preflight returned
exit 2 because no existing EULA acceptance was found. No EULA was accepted or
server started on the user's behalf. CI now runs that smoke step only with an
explicit owner-provided `PAPER_EULA_ACCEPTED=true` repository variable; otherwise
it reports the skipped check. The local helper writes `eula=false` by default.

No unit test claims to emulate a live Paper server. Real client gestures, native
NBT import, actual chunk-save/restart behavior and concurrent player use still
need the in-game checklist. Snapshot atomicity is machine-local, not a filesystem
transaction across playerdata and world saves. Unexpected third-party mutations
or an abrupt process crash still require reconciliation from backups. Unsupported
legacy serialization/stacked malformed lanes remain retained for administrator
inspection; no destructive repair command or bulk migration was added.

Deploy only the rebuilt ArcaneBrewery JAR, then restart Paper. The established
**Git Pull → Rebuild Project → replace JAR → restart** workflow remains valid.
This task does not change or require rebuilding ArcaneEconomy or the economy API.
