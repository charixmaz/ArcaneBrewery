# Mana conditions and retired systems

ArcaneAlchemy now has one consumption model. No configuration option can enable the removed historical progression.

| State | Condition | Symptoms |
| --- | --- | --- |
| Clear | Mana load below 12 and depletion below 25 | None |
| Mana Charged | Mana load at least 12, below overload, depletion below 25 | None |
| Mana Overload | Mana load at least 70 | Brief Slowness and Weakness |
| Mana Depletion | Load below 70 and depletion at least 25 | Brief Weakness |

Elixir strength, quality and magical potency determine mana load using the existing fantasy formula. Overload builds temporary depletion. Load recovers by 4 points per minute and depletion by 2, including offline time. These are exposure/recovery measures, not a spendable mana resource or a new spell system. Unrelated potion effects are never removed.

The previous optional consumption mode, predefined recipe catalogue, ingredient-to-old-recipe bridge, random-star cask scoring and unused process/hygiene engines have been deleted. The Codex displays current systems only. New extraction always uses the current process; saved canonical operations retain their captured quality/effect generation so upgrades do not reroll them.

Casks accept completed cultivated bases. Existing unsupported stored bottles can be withdrawn unchanged, without processing, conversion, or a discovery. The removed solution producer opens for administrators only to recover its saved contents; insertion and production are disabled. Its old storage key remains so contents are not lost.

Existing player data imports only temporary exposure if no current mana record exists. Historical progression fields stay untouched and inactive. Current fantasy PDC keys, item namespaces, machine snapshots, database records, private recipe fingerprints and named bottles remain readable. Old unused configuration entries are ignored; owners may remove them from their existing config. Customized messages and player-chosen bottle names are preserved, so server owners should review their own wording separately.

Validation: regression tests cover overload-to-depletion recovery, thresholds, deterministic offline recovery, invalid values and non-destructive player data import. Live Paper upgrade, old item withdrawal and UI checks still require server-owner testing.
