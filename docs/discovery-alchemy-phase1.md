# Discovery and alchemy expansion — Phase 1

Target: Paper 26.1.2 / Java 25. Fantasy game balance, not chemistry. This phase adds material exploration and reproducible effect profiles; it does **not** implement XP research, specialist machines, levels, or purification.

## Material exploration and Codex

There are **8 obtainable ingredient entries**: Wheat, Sugar Cane, Apple, Golden Apple, Potato, Sweet Berries, Honeycomb, and canonical Exclusive Brewer's Grain. The existing 25-entry ingredient registry is retained; ingredients without a normal supported washing/acquisition path are not advertised as discovery unlocks. Premium grain already has a rare mature-Wheat harvest drop and uses canonical metadata; renaming ordinary Wheat cannot create it. Its existing washing path is retained.

There are **26 raw catalyst/reagent entries**: 20 effect catalysts, 3 duration reagents, 1 potency reagent and 2 stabilizers. There are no new prepared items or obtainable purification reagents. All exposed entries have an implemented use in the existing Arcane Infuser. Blaze Powder and Blaze Rod are distinct: fire resistance versus combat strength, not cosmetic aliases.

A player's first actual possession records `(player UUID, kind, stable registry ID)`. Ingredients and catalysts are separate collections; Golden Apple can unlock both. Item count and display name are not identity. Registered canonical custom ingredient metadata wins over its carrier material. Unknown entries use a neutral pane, `???`, and a generic acquisition hint, without real names, properties, effects or recipes.

The Codex has separate ingredient, catalyst/reagent, independent-recipe, learned-recipe and public-metadata sections. Material pages show unlocked/total progress. Known material entries describe broad roles, actual machines and washing restrictions, not exact combinations. Public starter instructions retain ordinary Wheat production without enumerating the advanced catalog. The historical compatibility page no longer dumps advanced material references; existing legacy bottles remain inspectable and all 30 compatibility profiles remain in the implementation.

Material acquisition never grants recipe independence or changes LEARNED provenance. Recipe details continue to use recipient-specific full/partial projections and permission checks. Knowing a shared recipe does not fabricate material-acquisition history.

### Event and fallback behavior

Successful pickups, craft/trade/container inventory transfers, harvesting and joining schedule a scan of **resulting player inventory on the following server tick**. Craft/trade result clicks are hints only: recipe previews, hovering, opening a chest and canceled transfers do not supply item evidence. The scan never traverses a viewed container or virtual GUI. Display-only Codex/control items carry an explicit non-discoverable marker.

A round-robin fallback scans at most **8 online players per second**, at most the player's normal inventory and cursor, for command/other-plugin additions. Existing inventories reconcile on join and after plugin enable. Before showing locks the Codex reconciles current possession. Verified committed owner-machine INPUT slots also reconcile on a valid start, preventing an immediate command-added item from being missed when moved/used before the fallback. Decorations and previews are never supplied to that route. Material knowledge does not block machine use.

There is one pending persistence request per player; observed IDs coalesce in a catalog-bounded set. A dedicated single-thread worker has a 256-request queue. No material observation/query runs SQL on the server thread. Database uniqueness arbitrates notifications, including concurrent/repeated observations. Simultaneous discoveries produce one short two-line batch notice, at most three names plus a remainder count. Disconnect removes session/cursor/scheduling state; shutdown cancels scanning and drains bounded persistence work before storage closes. Failed writes retain observations for retry; current inventory provides reconciliation after restart.

## Typed raw catalog

All doses below are one item per selected input slot. Two identical catalysts consume one from each slot and record the combined dose. Larger retained old stacks still consume exactly one per selected slot. Inputs are ordinary vanilla raw items; there is no fictional preparation/acquisition step. Whole reagent items are consumed, including their containers where applicable.

| Raw material | Role / primary family | Base duration before workmanship/modifiers |
| --- | --- | --- |
| Blaze Powder | Fire Resistance (existing ID) | 40s |
| Amethyst Shard | Invisibility (existing ID) | 30s |
| Glistering Melon Slice | Regeneration (existing ID) | 12s |
| Glowstone Dust | Night Vision (existing ID) | 45s |
| Shulker Shell | Levitation, deliberately risky (existing ID) | 6s |
| Chorus Fruit | Short safe-destination teleport (existing ID) | one attempt |
| Netherite Scrap | Curse, deliberately risky (existing ID) | 12s |
| Wither Skeleton Skull | Wither, deliberately risky (existing ID) | 5s |
| Blaze Rod | Strength | 25s |
| Iron Nugget | Haste | 35s |
| Sugar | Speed | 40s |
| Prismarine Crystals | Water Breathing | 45s |
| Nautilus Shell | Dolphin's Grace | 20s |
| Ghast Tear | Regeneration | 20s |
| Rabbit Foot | Jump Boost | 35s |
| Phantom Membrane | Slow Falling | 30s |
| Turtle Scute | Resistance | 20s |
| Golden Apple | Absorption | 30s |
| Echo Shard | Hallucination, deliberately risky | 8s |
| Dragon Breath | Strength + Resistance, deliberately risky | 15s |
| Redstone Dust | Duration ×1.5, power ×0.75 | consumed modifier |
| Redstone Block | Duration ×1.8, power ×0.75 | consumed modifier |
| Lapis Lazuli | Duration ×1.25, power ×0.9, stability +5 | consumed modifier |
| Glowstone Block | Power ×1.6, duration ×0.65, stability −8 | consumed modifier |
| Amethyst Block | Stability +18 | consumed stabilizer |
| Nether Quartz | Stability +10, power ×0.8 | consumed stabilizer |

Duration/potency/stability changes are explicit registry data. Modifiers require an active effect catalyst; two modifiers alone are rejected without consumption. Active catalysts contribute full reaction load, duration/potency reagents one-quarter load, and stabilizers no active-catalyst load. Diversity penalties count active catalyst identities. Stability properties adjust the existing deterministic failure model; a stabilizer genuinely reduces risk rather than being penalized as another active catalyst. Existing eight-ID reactions retain their prior load/diversity behavior and explicit historic precedence.

## Reproducible profile V1

A **newly started extraction** captures effect generation 1 in its persisted process envelope; completion stamps `batch_effect_generation=1` onto the real base. A newly started infusion also captures this policy in its persisted input snapshot. Existing active extractions/infusions without the marker finish on the legacy path. New-policy batches produce a stored profile at bottling from actual formulation, deterministic workmanship, contamination and captured equipment/reaction stability. New infusion of an older canonical base uses deterministic recorded conditions for its profile assessment without rewriting the base's historical quality model.

`EffectProfile` V1 contains capability, ordered primary/side-effect entries, seconds, zero-based amplifier, safety and stability. It has a bounded binary codec under separate `drink_effect_profile_format=1` / `drink_effect_profile` metadata. The old bottle format and single-effect fields are retained. No display name selects a version. Missing metadata selects legacy behavior; partially present/unknown/corrupt new metadata fails closed, retaining the bottle rather than applying fallback benefits.

For uninfused new-policy batches, explicit natural definitions give short tier-I effects: Apple Regeneration 6s, Golden Apple Absorption 12s, Honeycomb Regeneration 8s, Sugar Cane Speed 15s, Sweet Berries Night Vision 15s, Potato Haste 8s, premium grain Haste 12s. Ordinary Wheat stays a non-effect base. A defined ingredient must comprise at least half the formulation; explicit priority resolves ties. An effect catalyst steers the result instead of adding free extra natural effects. Existing advanced ingredient mappings remain explicit and risky only for the deliberately dangerous materials; high affinity alone never invents hallucinations. These natural results are learned through experimentation, not exposed in locked in-game entries.

The profile calculator has **no UUID or random seed argument**. Workmanship is the deterministic quality assessment of actual ingredients, formulation/culture, processing and maturation; recorded contamination/equipment conditions remain distinct. It does not use random sensory/potency offsets as a benefit roll. Exact same inputs and recorded conditions produce the same stored profile. Existing formulation identity, personal names and permanent server-first recognition are unchanged; precise seconds, random variation and profile execution do not create identities.

Execution factor is `0.35 + 0.65 × quality/100`. Zero workmanship/unfinished batches receive no profile benefits. Uninfused natural effects are capped at tier I. Infused eligible families receive amplifier 1 (tier II) at quality >=90 with normal power, or quality >=80 with power >=1.5, always constrained by family caps. Duration is base × duration multiplier × execution; increased power divides duration by its square root, and tier II further multiplies duration by 0.75. Fractional seconds round down with a one-second minimum. At a family cap extra potency grants no extra tier; modifiers are not guaranteed upgrades. Duration reagents can lower an eligible tier, and extend associated mild drawback exposure.

The starter infuser permits at most **two primary effects**, selected using explicit reagent priorities, never alphabetical identifier order. Duplicate families coalesce. Haste has mild Hunger (up to 20s); Strength has modest Slowness (up to 12s). Ordinary successful utility profiles do not add nausea or blindness. Speed with Strength, Levitation with Jump Boost/Slow Falling, and dangerous transformations mixed with utility are rejected before start. No supposedly useful mixture cancels its own benefit. Deliberately risky transformations retain bounded dangerous outcomes; unsafe equipment/contamination also produces explicit caution/danger. Not every successful elixir has a drawback.

### Starter capability caps

| Effect family | Maximum duration | Maximum tier |
| --- | --- | --- |
| Haste, Speed, Jump Boost | 60s | II |
| Strength | 30s | I |
| Resistance, Dolphin's Grace | 25s | I |
| Regeneration | 20s | I |
| Fire Resistance, Slow Falling | 60s | I |
| Night Vision, Water Breathing | 90s | I |
| Invisibility, Absorption | 45s | I |
| Levitation | 6s | I |
| Teleportation | one safe destination attempt | no tier |
| Hallucination | 8s | I |
| Curse | 12s; its Weakness component at most 10s | I |
| Wither | 5s | I |

Dangerous profiles can include a short Weakness backlash (up to 10s). No ten-minute tier-V profile is available from the starter machine. `EffectCapabilities` is the boundary for later machine-specific policies, not a generic tier cap shared blindly between Haste and Resistance.

Consumption reads the stored profile and builds a unique execution plan. It does not recalculate or roll benefits. Native potion effects are not placed on bottles for tooltip decoration. Profile effects and temporary condition symptoms coalesce by native type before one application; the legacy single-effect path remains separate. Teleport is attempted once using the existing safe-destination routine. First-60-second poison overrides benefits; unfinished zero quality, severe over-aging, hidden maturation targets and actual recorded harvest times remain intact.

New-profile lore contains name, italic producer, quality, a brief effect/drawback summary and important warnings, with `/brew inspect` for details. Inspection shows stored durations/amplifiers, safety/stability, quality factors and actual harvest time. It does not reveal ideal maturation times/countdowns/readiness. Historical bottle statistics are not rewritten. No automatic factor-by-factor completion chat is introduced.

## Persistence and compatibility

SQLite V4 adds only `material_discoveries`. Its primary key is player/kind/stable registry ID. Existing schema checksums, recipes, exact process histories, aliases, names, grants, server-firsts and market records are untouched. A consistent recoverable `VACUUM INTO` backup is created before upgrading an older schema, and migrations are transactional and repeatable. No database/world reset is required. Before material tracking existed, historical acquisition cannot be inferred; existing inventory reconciles and earlier recipe knowledge is preserved independently.

Stable IDs survive catalog additions and label changes. There is no XP deduction, machine-level state, required external integration, economy API change, monetization or shared-machine ownership. Existing inventory/session/automation protections remain. One-time material discovery is not a global ownership claim and cannot overwrite another player's progress.

## Later roadmap — not implemented here

Research has three **separate** requirements: personal knowledge, one-time XP research, and physical construction resources. Research unlocks belong to the player; physical upgrades belong to that machine. Earned machine unlocks must be explicit durable flags/records, not a percentage of a growing catalog, so adding materials cannot relock them.

Future specialists: Reagent Processor (real prepared-item acquisition), Duration Infuser (resource-funded duration), Essence Concentrator (strength with duration/yield tradeoffs), Purification Chamber (remove one selected drawback at a material, duration or yield cost). Low-tier utility may eventually reach 5–10 minutes; high-tier combat effects need stricter caps. Every specialist operation consumes resources; no repeated free amplification loop. These are Phase 2+ design requirements, not available machines or commands in Phase 1.

## Verification and in-game checklist

Automated coverage exercises typed catalog roles and caps, compatible deterministic combinations, conflicts, modifier consumption and slot-order invariance, stored versioned profiles and legacy absence, unique effect plans, custom ingredient identity, locked projections, canceled/virtual/preview acquisition hints, compact notices, independent per-player and per-kind progress, restart/concurrent persistence, additions without erasure, and V3-to-V4 backup/migration. Existing private/shared recipe, inventory/session and maturation regressions remain part of Maven verification.

Live Paper checks still require the existing owner-provided EULA gate. Do not accept an EULA automatically. After Git Pull → Rebuild → replace Brewery JAR → restart Paper: join with supported items; collect/craft/trade/harvest/take container items; try canceled transfers and viewing locked pages; obtain a dual-role item and canonical premium grain; test compatible/invalid infusions and quantities; inspect/consume both new and legacy bottles; test early poison/severe over-aging and hidden targets; restart/unload; verify owner/session/hopper protections. No economy API rebuild is required.
