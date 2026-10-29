# Phase 2 — machine unlocks and specialist processing

Target: Paper 26.1.2, Java 25. This document supersedes Phase 1 descriptions of sharing, repeatable naming, contamination, and specialist availability. No economy or economy API source changes or new dependencies are required.

## Player release changes

* `/brew share`, `/brew revoke`, grant routes, their completion/help, and sharing buttons are removed. Players exchange instructions outside the plugin. Historical grants and LEARNED provenance remain readable through the existing permission projections; public discovery names never reveal another player's exact process.
* `/brew rename <held|short-id> <name> confirm` spends the **one permanent rename for that player's formulation**. Without the final `confirm`, the command displays the irreversible rule and makes no change. Every database update is guarded; another bottle, an exact-process alias, concurrent requests, and restart do not reset it. Existing names count as used. Visibility changes remain possible after naming.
* `/brew guide` has a starter journey, material collections, independent/public discoveries and icon-based machine build tutorials. Learned Recipes and Specialist Research pages are removed. Machines & Controls shows red barrier icons for locked specialists with their name and XP cost; left-click unlocks immediately, right-click opens the build guide. Tooltips use spacing and colored labels; details are split into short cards. Existing hidden maturation targets remain hidden.
* There are **8 obtainable ingredients and 29 reagent discoveries**: the existing 26 raw reagents plus three genuinely crafted prepared reagents. All **37** locked entries have distinct acquisition riddles; unlocked descriptions replace the riddles. The broader 25-entry ingredient library is retained, but unsupported ingredients are not advertised as usable unlocks.
* Shift-click routes accepted player items to actual input slots, merges partial stacks within slot caps, and transfers outputs only when inventory space exists. Extraction uses bottle slots 1–3 and mixing slots 5–6; starter infusion uses one item per base/catalyst slot. Cultivation's native barrel retains ordinary transfers; its separate controls also accept shift-insertion into the physical barrel. Maturation lanes retain their timestamp/harvest conversion and do not drop outputs when inventory is full. Decorations, controls and guide icons cannot supply inputs. Number-key, offhand, creative, drag and automation restrictions remain.

## Persistent personal unlocks

Unlocks and XP levels are saved together in the **same Minecraft player-data save**, using versioned `arcanebrewery:research_v1` PDC data. There is no SQL-versus-XP two-store transaction. The server thread rechecks current levels and persisted earned flags at the purchase click, deducts only whole Minecraft levels (not points), then calls `Player.saveData()`. A failed save restores the in-memory XP level/progress/total and prior metadata. Repeated clicks cannot charge an earned unlock. Unknown versions/types fail closed. Keep normal server/player-data backups, as with all vanilla inventory and XP persistence.

Earned flags use stable machine IDs. Existing earned unlocks are preserved. There are **no material, recipe, percentage or prior-machine prerequisites**. The four ordinary production machines remain available without an XP unlock.

| Machine | One-time XP levels | Physical core above foundation | Construction in slots 1 / 2 |
|---|---:|---|---|
| Reagent Processor | 50 | Stonecutter / Copper Block | 4 Iron Ingots / 8 Copper Ingots |
| Duration Infuser | 50 | Loom / Redstone Block | 4 Gold Ingots / 2 Redstone Blocks |
| Essence Concentrator | 50 | Smithing Table / Iron Block | 2 Diamonds / 1 Iron Block |
| Purification Chamber | 50 | Grindstone / Amethyst Block | 4 Amethyst Blocks / 2 Gold Ingots |

A player at level 50 becomes level 0; level 63 becomes level 13. Insufficient XP changes nothing. The cost is 50 levels, not all the player's XP.

An unlock belongs to the player. Construction belongs to that physical core, not every machine the player owns. Place the two specified blocks, sneak-right-click the core, insert construction resources in its first two slots and click **Construct**. Resources are consumed from the persisted machine inventory in the same snapshot as its built flag. Reopening does not rebuild or charge again. Extra resources remain retrievable. Breaking an empty constructed core discards its construction investment; nothing is refunded or transferred to a replacement. Owner and exclusive viewer leases use the existing machine-access boundary. The core and foundation must remain intact to operate/recover; structure restoration resumes a retained operation.

## Working processes and costs

All specialist operations take **10 seconds**, including offline time. First two GUI slots are inputs; third is output. Inputs and outputs are locked while processing. A valid start atomically consumes machine inputs and stores the exact computed output with the deadline in the same CAS-protected chunk snapshot. Completion only releases that stored output. Restart, unload/reopen, repeated completion and stale sessions cannot reroll or recharge it. Invalid input, absent purification target, full output or repeated processing rejects without consuming items. No marketplace or shared ownership is added.

The **Reagent Processor** creates one canonical prepared reagent per operation:

| Raw input (slot 1) | Binder (slot 2) | Output |
|---|---|---|
| 4 Redstone Dust | 1 Amethyst Shard | 1 Prepared Duration Reagent |
| 4 Glowstone Dust | 1 Blaze Rod | 1 Prepared Potency Reagent |
| 4 Nether Quartz | 1 Honeycomb | 1 Prepared Purification Reagent |

Prepared reagents use a Prismarine Shard carrier with explicit versioned canonical metadata. An anvil name or ordinary shard cannot impersonate one. Prepared reagents are consumed by specialists, not the starter infuser.

The other machines consume **one finished versioned elixir plus one matching prepared reagent** and yield one elixir. They never change identity, original producer, quality statistics, naming ownership or server-first credit. They store the resulting effect profile, monotonic per-bottle processing flags, and a bounded versioned history containing operation/time and source/result profiles. Ordinary historical single-effect bottles remain drinkable and unchanged; they cannot be converted by specialists. Unfinished/poisonous bottles are rejected.

* **Duration Infuser:** multiplies primary durations by 12 within family caps and sets primary tiers to I. Existing mild drawbacks are not lengthened. A duration pass is allowed once per bottle.
* **Essence Concentrator:** raises eligible primary amplifiers by one, capped per family, while retaining only 35% of prior primary duration (rounded down, minimum 1 second). Families that cannot gain a tier are rejected unless another primary can. One concentration pass per bottle, one input/output bottle; the duration and reagent cost are real.
* **Purification Chamber:** cycle the selector, then remove exactly one selected Hunger, Slowness, Weakness, Wither, Curse, Hallucination or Levitation entry. Other effects and recorded reaction safety remain unchanged. Only one purification pass per bottle, at a consumed reagent/time cost. An absent target or beneficial non-removable effect rejects without consumption.

### Capability limits

Starter caps remain unchanged. The duration machine caps all primary effects at tier I:

| Family | Duration cap |
|---|---:|
| Haste, Speed, Jump Boost, Night Vision, Water Breathing | 600s |
| Fire Resistance, Slow Falling | 300s |
| Invisibility, Absorption | 120s |
| Strength | 45s |
| Regeneration, Resistance, Dolphin's Grace | 30s |
| Deliberately dangerous transformations | Existing starter caps: Levitation 6s, Hallucination 8s, Curse 12s, Wither 5s; teleport one attempt |

Concentration allows Haste/Speed/Jump Boost up to **tier III / 90s**, Strength up to **tier II / 30s**; all other families retain starter caps/tier I. These are maximums, not guaranteed outcomes. Applying concentration after duration still limits high-tier utility to 90s; applying duration afterward drops its tier to I. Three monotonic bits prevent alternating machines from producing unlimited loops. Purification preserves the incoming capability. Native effects still execute once from the stored profile, without tooltip-native duplication or random UUID benefit rolls.

## Removal of active contamination

The infection listener is no longer registered. Cask infection/spoil flags neither block use nor trigger cleaning, sign mutation or spoil timers. Infusers do not accumulate contamination; captured historical contamination remains readable but does not affect current reaction assessment. Cleaning resources are no longer charged. Integrity repair, dangerous catalyst reactions, badly chosen cuts, unfinished quality zero, explicit first-60-second poison and severe over-aging remain.

Active production neutralizes historical sanitation/oxygen/oxidation/cleanliness dimensions for the removed contamination mechanics while preserving the original process record/fingerprint. Measured storage temperature, ingredient purity, culture, cuts, maturation and equipment integrity/stability still matter. New production does not use the archival contamination evaluator. New profile calculation ignores contamination; consumption does not accumulate its hazard or apply contamination poison symptoms, including for old bottles. Already stored bottle quality/value/profile/safety fields are not rewritten or rerolled. Historical dangerous stored profiles continue to execute their recorded effects; deliberate dangerous effects and early poison are not treated as contamination.

**Existing active batches:** keep their actual ingredient, culture, time, equipment and process snapshots. When harvested/completed after the upgrade they use the contamination-free active policy, without deleting their old measured fields. Their captured timers, immature gate, first-60-second poison and over-aging are preserved. Existing finished bottles retain their stored statistics and old single-effect compatibility. The old industrial Cleansing Producer remains only a compatibility route for retained industrial bases; its solution no longer has a cask-cleaning requirement or use and is not advertised as progression.

## Migration and deployment

SQLite **V5** adds a rename-used field and guards to the existing personal formulation table. Previously named records are marked used without changing names; insert/update triggers protect all persistence entry points. Old migration checksums are unchanged. The existing consistent recoverable pre-upgrade backup and transactional migration apply; no database/world reset or historical record deletion occurs. Research is additive player PDC; specialist ownership/inventory/build/process are additive chunk PDC. Do not downgrade across these new records without restoring the corresponding backups.

Deployment remains **Git Pull → Maven rebuild → replace Brewery JAR → restart Paper**. No economy API rebuild or new service is required. Back up the Brewery database, worlds/chunks and player data before upgrading. Paper smoke/live tests require the server owner's existing EULA acceptance; this release does not accept it automatically.

### Server-owner checklist

1. Join with known materials; inspect locked riddles, known descriptions and independent recipe privacy. Confirm sharing commands/completion/buttons are absent.
2. Rename a personal formulation with confirmation; try another bottle, old exact-process alias, restart and a second request. Verify the first name remains.
3. Unlock with 49 levels (refused), then 50 levels (becomes 0). Confirm only one charge, permanent flags after restart, and separate construction on a second core.
4. Build each structure, deposit resources and construct. Test owner/second-viewer/hopper/number-key/drag restrictions and full/partial inventories with shift-click.
5. Make all three prepared reagents, then test duration, concentration and selected purification. Try repeated passes and wrong/absent inputs; verify no free modifiers or input loss.
6. Restart/unload during processing; restore the structure if interrupted; collect exactly one stored output. Check original name/identity/producer and stored quality remain.
7. Check old single-effect bottles and stored profiles, active legacy batches, early poison, unfinished zero quality and severe over-aging. Contaminated historical casks should work without cleaning, with hidden maturation targets and compact lore preserved.

Automated tests cover persistence races, rename aliases/restarts/guards, XP save rollback and duplicate confirmations, XP-only unlocks, prepared recipes and consumed quantities, machine snapshot/CAS recovery, effect caps/order/loop prevention, selective purification, legacy profiles, acquisition riddles and shift conservation. Native Paper startup and live player checks must be reported separately from those API-boundary tests.

## Cosmetic bottle colors

Finished elixirs use the first stored primary effect for their liquid tint (for example Speed blue, Haste gold, Strength red). Plain elixirs use amber; refined and cultivated bases use gold and violet. The refresh changes only visual color and lore, never native potion effects, effect profiles, durations, tiers, identity or historical statistics. Existing recognized bottles refresh through the normal presentation listener; ordinary vanilla water remains unchanged.

## Processor access

Stonecutter directly above Copper Block is the processor structure. Interacting with either structure block resolves the same saved core. A locked machine explains the XP unlock location. An unlocked, unclaimed structure explains the sneak-right-click action rather than silently opening the vanilla workbench. Claim with sneak-right-click, insert 4 Iron Ingots and 8 Copper Ingots into the first two GUI slots, then Construct. After construction, use the raw recipes above. Existing owner/session and protection-event denials remain enforced. The build guide includes input and output icons for all three prepared reagents.
