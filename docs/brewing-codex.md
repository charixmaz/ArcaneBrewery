> Current controls, personal discovery/naming, and migration semantics are specified in [Brewing gameplay V2](brewing-gameplay-v2.md); those rules supersede the older behavior below.

# Brewing Codex

Open `/brew guide` (alias `/brew codex`). `/brew help` links to it.
This is a read-only inventory guide: it grants no items and cannot start a machine.
Ordinary clicks navigate; shift-click, hotbar/offhand swaps, double-click, creative
inventory gestures and drags cannot transfer reference icons.

## Contents

- Machines: washing cauldron, four current machines and the legacy cleansing
  producer. Includes structures, inputs, configured durations and maintenance.
- Ingredients: all entries in the runtime ingredient registry. Only the same seven
  materials accepted by the actual cauldron handler are marked starter-ready.
  Registry-only ingredients and special premium metadata are explicitly labeled.
- Catalysts: all eight supported registry items, their affinity and safety limits.
  Combining catalysts does not promise stacked effects.
- Starter walkthroughs: plain Wheat, plain Apple, and Wheat with Ember infusion.
  These are public production instructions, not predefined discoveries, guaranteed
  outputs, or new item crafting recipes. They use server processing settings.
- My Discoveries: paginated owned results, with exact process access rechecked on
  each recipe open. Pagination is not limited to the first 200 discoveries.
- Public Discoveries: paginated safe public metadata. PUBLIC identifies a recipe;
  it does not automatically disclose the process.
- Shared recipes: `/brew guide recipe <full fingerprint>` uses the existing
  recipient-specific knowledge projection. Unknown/identified/partial/full states
  stay distinct. There is no invented shared-recipe index.
- Brewing Knowledge: naming, discovery identity, saturation, contamination and care.
- Legacy Profiles: 30 existing compatibility profiles, with fantasy reference
  labels, actual required woods and legacy timing windows. They require older
  stage-tagged bottles; the modern extractor does not create those stages. They
  are not seeded modern discoveries or a new survival recipe catalog. Legacy
  outputs remain compatibility elixirs without procedural registration.

Async replies return to the main thread, and are discarded if the player closed
or replaced that exact guide view. Recipe access is freshly queried; no authoritative
process or grant table is passed directly to the inventory adapter.

## Names and recipe identity

New fallback names describe wood, dominant ingredient and presence of additives:
`Oak-aged Wheat Arcane Elixir`, for example. Names are deterministic descriptions,
not unique identifiers or guaranteed effect names. Built-in `minecraft:` prefixes
are hidden; custom namespaces remain recognizable. Existing persisted discovery
names and player renames are not rewritten. The original discoverer can rename
once through the existing `/brew rename <full fingerprint> <name>` command.
Changing generated names does not change fingerprint encoding or old recipe IDs.

There is no configured discovery count cap and no fixed list of all modern elixirs.
Fingerprint identity includes normalized ingredient ratios, preparation,
cultivation catalyst/temperature/duration, extraction cuts/temperature/passes,
wood/maturation duration stage and additive identities/doses. Batch randomness,
contamination and quality are not extra recipes.

The current UI exposes only one extraction ingredient slot and up to two
catalysts per infusion, with fixed default extraction controls and cultivation
conditions. The domain supports more flexibility than players can currently select.
One infusion with eight catalyst types has eight single choices and 36 unordered
pairs with repetition, plus skipping infusion (45 choices). This is NOT a count of
unique effects or a guaranteed total discovery count: wood, stages, existing
preparations, failures and reachability also matter. At default timing, 121s and
126s occupy the same mature identity stage. A different mature 30-second stage may
create another identity. Reproducing an existing recipe never transfers ownership.

## Verification and live checks

Use the existing Maven/CI suite. Live Paper GUI rendering is still a separate check:

1. Open all sections and navigate back/home/close.
2. Check registry-only ingredients cannot be mistaken for washable starter inputs.
3. Try shift-click, number keys, offhand swap, double-click and dragging with real
   player items; no guide icon or player item should move or disappear.
4. Browse owned/public pagination; open a shared full ID with two different users.
   Public names must not expose a private process; partial sharing stays partial.
5. Close the guide while a query runs; its reply must not reopen or replace another GUI.
6. Run a starter walkthrough with server timings and inspect the resulting name,
   metadata, fingerprint and normal discovery ownership.

No economy API change or rebuild is required. No Paper EULA acceptance is changed.
