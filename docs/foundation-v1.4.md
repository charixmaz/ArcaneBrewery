# Foundation V1.4: ingredient and batch integration

## Identity and catalog

Domain/application ingredient code uses namespaced string identifiers and never
Bukkit Material or ItemStack. IngredientIds normalizes case/outer whitespace and
validates namespace:path syntax, up to 256 characters. Existing V1.1 process
identities and persisted discoveries are not rewritten or silently aliased.
New item extraction emits namespaced IDs; any future migration from old unqualified
process IDs must explicitly preserve or migrate drink fingerprints.

IngredientDefinition groups a composition profile, player-facing name, premium
marker and optional default quality modifiers. IngredientRegistry is an injected,
immutable catalog with explicit external-item-key mappings. Duplicate definitions,
normalized mapping collisions and references to unknown definitions are rejected.
The plugin creates one registry/adapter at enable and shares it with harvesting
and washing. BuiltInIngredients contains all initial composition defaults.
Future configuration can construct a replacement catalog with tuned definitions
and mappings without duplicating balance constants across listeners.
There is no new YAML surface or global mutable registry.

## Built-in Minecraft mapping

Each listed minecraft ID maps to the matching Material key. Premium grain is an
explicit metadata identity carried by wheat; ordinary wheat remains minecraft:wheat.
Only brown mushroom appears in the legacy conversion task; red mushroom is included
as its supported catalog variant. Wither skeleton skull and prismarine crystals
are included because they are used by legacy logic.

All numbers below are 0..100 game-balance defaults, not real chemistry or safety
claims. Columns are sugar, starch, acidity, bitterness, aroma, toxicity and affinity.
They are hidden simulation inputs; lore does not expose them.

| Ingredient ID | Display name | Composition |
| --- | --- | --- |
| minecraft:wheat | Wheat | 20 / 80 / 5 / 10 / 40 / 0 / 0 |
| minecraft:sugar_cane | Sugar Cane | 75 / 10 / 10 / 5 / 45 / 0 / 0 |
| minecraft:sugar | Sugar | 100 / 0 / 0 / 0 / 15 / 0 / 0 |
| minecraft:apple | Apple | 60 / 5 / 35 / 5 / 70 / 0 / 5 |
| minecraft:golden_apple | Golden Apple | 55 / 5 / 30 / 5 / 85 / 0 / 75 |
| minecraft:potato | Potato | 15 / 90 / 5 / 8 / 25 / 12 / 0 |
| minecraft:sweet_berries | Sweet Berries | 60 / 5 / 55 / 10 / 85 / 0 / 5 |
| minecraft:honeycomb | Honeycomb | 90 / 0 / 10 / 0 / 90 / 0 / 10 |
| minecraft:kelp | Kelp | 10 / 10 / 25 / 45 / 45 / 5 / 15 |
| minecraft:melon_slice | Melon | 65 / 5 / 15 / 5 / 65 / 0 / 5 |
| minecraft:cactus | Cactus | 30 / 10 / 45 / 30 / 45 / 5 / 5 |
| minecraft:brown_mushroom | Brown Mushroom | 5 / 20 / 15 / 40 / 50 / 20 / 25 |
| minecraft:red_mushroom | Red Mushroom | 5 / 15 / 20 / 50 / 45 / 40 / 30 |
| minecraft:cocoa_beans | Cocoa | 20 / 25 / 25 / 80 / 95 / 5 / 10 |
| minecraft:blaze_powder | Blaze Powder | 0 / 0 / 10 / 70 / 50 / 35 / 90 |
| minecraft:chorus_fruit | Chorus Fruit | 45 / 10 / 30 / 15 / 80 / 10 / 80 |
| minecraft:glistering_melon_slice | Glistering Melon | 45 / 5 / 10 / 5 / 80 / 0 / 65 |
| minecraft:glowstone_dust | Glowstone | 0 / 0 / 5 / 55 / 45 / 25 / 90 |
| minecraft:prismarine_crystals | Prismarine Crystals | 0 / 0 / 20 / 40 / 35 / 10 / 70 |
| minecraft:amethyst_shard | Amethyst | 0 / 0 / 0 / 10 / 60 / 5 / 85 |
| minecraft:netherite_scrap | Netherite Scrap | 0 / 0 / 0 / 90 / 5 / 80 / 75 |
| minecraft:echo_shard | Echo Shard | 0 / 0 / 10 / 65 / 65 / 45 / 100 |
| minecraft:shulker_shell | Shulker Shell | 0 / 5 / 15 / 40 / 45 / 20 / 85 |
| minecraft:wither_skeleton_skull | Wither Skeleton Skull | 0 / 0 / 35 / 95 / 10 / 95 / 100 |
| arcanebrewery:premium_grain | Exclusive Brewer's Grain | 30 / 85 / 5 / 5 / 70 / 0 / 20 |

Premium grain also defaults to +3 flavor/+3 potency modifiers. These are preserved
and weighted during extraction, but are not silently applied by the V1.1 calculator:
V1.5 must explicitly decide how such optional modifiers affect simulation.
Its own composition already provides distinct quality characteristics.

Custom definitions such as server:moon_grain or itemsadder:pack/grain can be registered.
External plugins may stamp explicitly registered canonical IDs through the adapter;
no ItemsAdder/Oraxen dependency or automatic display-name matching is introduced.
An unsupported Material with a forged display name or lore never becomes an ingredient.
Explicit PDC can identify a registered custom ingredient on another carrier Material.
PDC is trusted server item metadata, not a cryptographic authenticity mechanism.

## Actual ingredient instances

IngredientInstance holds ID, whole-percent purity, positive long quantity,
IngredientModifiers and optional IngredientProvenance (source ID/producer UUID).
Finite item modifiers are limited to -10..10. Quantity is always the actual item
count, not separately stored in PDC. Per-item purity remains an integer compatible
with legacy washing. Extracted combined lots can have fractional weighted purity.
Washing returns a new instance with +25 purity, capped at 100.

## Canonical PDC V1

The production namespace is arcanebrewery, derived from the plugin name.
IngredientMetadataCodec is the only ingredient metadata interpreter/writer.

| Key | Type | Meaning |
| --- | --- | --- |
| arcanebrewery:ingredient_format | INTEGER | Format version, currently 1 |
| arcanebrewery:ingredient_id | STRING | Registered canonical ingredient ID |
| arcanebrewery:ingredient_purity | INTEGER | The single numeric purity source, 0..100 |
| arcanebrewery:ingredient_flavor_bonus | DOUBLE | Optional flavor modifier, default 0 in canonical data |
| arcanebrewery:ingredient_potency_bonus | DOUBLE | Optional potency modifier, default 0 in canonical data |
| arcanebrewery:ingredient_source | STRING | Optional provenance source ID |
| arcanebrewery:ingredient_producer | STRING | Optional producer UUID; requires source |
| arcanebrewery:ingredient_lore | STRING | Owned presentation block for safe lore replacement; never identity |

Canonical format/id/purity are required together. Unsupported versions, invalid
types, unknown registered IDs and malformed modifier/provenance data fail explicitly
rather than falling back to vanilla identity. Canonical identity takes priority
over any stale premium flag. Newly written items carry canonical metadata and no
new legacy premium flag. Purity deliberately reuses the existing INTEGER key,
avoiding competing old/new purity values.

IngredientItemAdapter clones the item, updates only owned PDC/presentation and
keeps quantity, unrelated PDC, other item components and unrelated lore. It replaces
its recorded lore block on repeat washing and recognizes the old washer-generated
block during migration, preventing contradictory old purity percentages.
Names and lore are presentation only. Visible data is the ingredient name,
purity/washing status and premium marker, without exact affinity/toxicity/modifiers.

## Legacy purity and premium migration

Reads do not mutate old items. Migration occurs on a successful write/wash.

- ingredient_purity INTEGER remains canonical and backward compatible. Unmarked
  items default to 0 purity. Legacy out-of-range integers are safely bounded.
- premium_grain_tag STRING TRUE on wheat resolves to arcanebrewery:premium_grain
  with its own definition/modifiers. Case/outer whitespace are tolerated. Other
  materials or arbitrary renamed wheat do not inherit premium identity.
  Canonical rewrites remove the obsolete flag.
- washed_ingredient STRING presence remains readable and is preserved if already
  present. No numeric purity is invented from this boolean-style historical marker.
  The current washer never wrote this marker before V1.4; V1.4 likewise does not
  synthesize it. It is not an alternative numeric purity source.

IngredientPurityEngine delegates purity and washing to the adapter. The active
CauldronWasherEngine keeps the same right-click/main-hand interaction, seven
washable Materials, +25 increment, one-item handling, one water-level cost and
feedback/sound. Validation occurs before consuming water or the held stack.
The washable set remains wheat, sugar cane, apple, golden apple, potato, sweet
berries and honeycomb; a larger catalog does not silently expand live interactions.

AgricultureListener retains the mature-wheat check, 2% chance, world drop, quantity
and discovery feedback. New rare grain is stamped as canonical premium grain with
wheat-harvest provenance and producer UUID. Old premium items still resolve.
Its presentation no longer advertises legacy star-roll probabilities.

The active DistilleryMenuHandler is unchanged, including its numeric purity
transfer onto wort using the same ingredient_purity key. Those potions are not
ingredients merely because they carry purity. Its premium-grain recipe behavior
also remains unchanged; V1.4 does not replace its Material-based recipe switch.

The separate DistilleryProcessTask is not registered/called by current plugin
startup. Only its two old metadata reads delegate to the compatibility facade;
its conversion rules, timing, deductions and outputs are unchanged. Its historic
washed-marker requirement remains intact and is deliberately not converted to a
new numeric-purity gate in this milestone.

## Batch extraction and ratios

MachineIngredientExtractor is a read-only Bukkit boundary. It accepts supplied
stacks or explicit inventory input slots; callers must omit GUI decoration,
fuel, equipment and output slots. Duplicate/out-of-range slots are rejected.
It never consumes items or starts machines. Null/air/empty slots are ignored;
unsupported items produce UnsupportedIngredientException instead of disappearing.

IngredientInput distinguishes actual ingredients, empty containers and base liquids.
Glass bottles/empty buckets are containers. Water buckets and plain water potions
without custom effects or brew_stage are base liquids. Legacy wort and other potions
are rejected. Liquid counts are item units, not liters or equivalent fluid volumes.
Container/liquid counts remain in the result and do not affect ingredient ratios.

The pure BatchIngredientExtractor combines identical IDs into sorted immutable
portions. It preserves long quantities, quantity-weighted purity/modifiers and
provenance counts (including unknown provenance). Exact decimal weighted sums
make order and stack splitting irrelevant. Count overflow fails explicitly.
Regular wheat and premium grain never combine.

ExtractedIngredients.ratios() divides each ingredient quantity by the total using
DECIMAL128, producing a stable sorted map. Three wheat and one honeycomb become
0.75/0.25. Identity is independent of slots or lore.
simulationLots() returns V1.1 IngredientLot values with the catalog composition
and exactly the extracted weighted purity. No complete ProcessProfile, brewing
process, fingerprint registration or database writes occur here.

## V1.5 boundary and verification

V1.5 can inject this same item adapter/extractor into the distillery, explicitly
select machine input slots and use ratios()/simulationLots() when constructing
processes and batch conditions. It must define volumes, catalysts/fuel roles,
modifier effects and any transition away from legacy recipe switches separately.
The new extraction service is not called by live distillery/cask machines yet.
No storage schema, recipe codec, economy, GUI, progression or intoxication changed.

Tests cover every built-in Material mapping, unsupported/renamed carriers, legacy
and canonical metadata, premium migration, washing/caps, unrelated metadata/lore,
custom IDs, quantity/weighted-purity transfer, deterministic ratios and inventory
ordering, containers/liquids, validation, duplicate definitions and source dependency
boundaries. Small ItemStack/ItemMeta/PDC API doubles test adapter behavior without
starting Paper or adding mocking dependencies; full live-server interaction testing
is still a deployment smoke-test responsibility.

Run mvn clean verify with Java 25. As in prior milestones, this Windows checkout's
existing target has delete-deny permissions; verification uses a fresh temporary
source/POM copy and checks that its contents match the committed sources exactly.
