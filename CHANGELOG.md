# Changelog

Every change must leave a trace here. Entries describe behavior, compatibility, verification and remaining limits; planned work must not be listed as released.

## Unreleased — cultivation multiblock

### Fixed
- Empty cultivation machines are recognized from a horizontal Barrel and a wooden Fence directly behind it, rather than requiring a bottle to trigger recognition.
- New cultivation starts require the rear fence. Existing owned barrels, bottles and captured progress remain recoverable; restore the structure to start another batch.
- Rear fences participate in machine ownership and occupied-machine structure protection.

### Changed
- Build icons and machine instructions describe the rear-fence structure, normal inventory click and sneak-click controls.

### Verification
- CI pending. No live Paper checks performed.

## Earlier — production interfaces

### Added
- Expanded Extractor, Cultivation, Arcane Infuser and Maturation Cask interfaces to 27 slots. Inputs retain saved indexes; controls and status occupy separate rows. Casks expose elapsed time only.

### Changed
- Separated production controls and status from saved item lanes without migrating stored inventories.
- Old invisible button positions no longer trigger actions; shift-click still uses the existing item indexes.

### Verification
- Pending final CI. Native Paper GUI, restart and protection-plugin tests have not run.

## 2026-10-29 — `7abd91f` (#6)

### Added
- Permission `arcanebrewery.rename.colors` (default false): color-only `&0–&f` in the one permanent name. No payment integration; owners assign permissions to ranks.
- Persisted cosmetic tasting notes on newly finished bottles. Refresh and specialist processing retain the note.
- Cumulative drawback-duration reduction using Sugar (1s each / 25s cap), Honeycomb (3s / 35s cap) or Amethyst Shard (5s / 45s cap). At least one second remains; benefits and reaction safety remain unchanged.
- Portable nine-bottle crates: `/brew crate pack <1–9>`, `inspect`, `unpack`. Packing consumes one ordinary Barrel; unpacking returns it. Stored bottles retain native metadata. Crates cannot be placed or nested. Full inventories reject safely.
- Expanded specialist controls with a real processing clock.
- GitHub overview, documentation index, owner guide and contribution/change-record conventions.

### Changed
- Purification consumes ordinary reduction materials as an alternative to prepared reagents. Existing prepared purification reagents remain usable at 5 seconds each, with the same 45-second cap. Historical bottles already fully purified cannot receive this treatment. Existing in-flight operations retain their stored output.

### Verification
- 498 automated tests, Maven verification and standalone packaging passed in CI before the final control-text alignment. Final commit CI is rechecked before merge. No live Paper, crate or protection-plugin checks claimed.

### Still pending
- Native GUI, protection-plugin, crash/restart and in-game balance testing.

## 2026-10-29 — `82ca534` (#5)

### Fixed
- Removed residual seeded secondary-stat variation from V3 production; V2 remains unchanged.
- Processor uses Smooth Stone for new structures and accepts all old ordinary/waxed copper oxidation stages.
- Unnamed bottles and personal records consistently display Unnamed Elixir. Existing personal names and historical temporary-name fields are retained.

### Verification
- 486 tests, Maven build, standalone packaging and CI passed. Live Paper checks skipped.

## 2026-10-29 — `5edd3b8` (#4)

### Added
- Mixture-dependent V3 quality and maturation; captured policy in extraction state and item metadata preserves old batch rules.

### Changed
- Removed perfect Wheat/Apple walkthroughs and ideal-setting disclosures from player controls and guide.

### Verification
- 485 tests, Maven, packaging and CI passed. Live Paper checks skipped.

## Earlier changes

- `9db5e0b` (#3): 50-level specialist unlocks, icon structure guides, processor access guidance and cosmetic bottle colors; 474 tests and CI passed.
- `dfd3536` (#2): standalone economy removal and discovery/cask/recipe/performance reliability fixes; 464 tests and CI passed.
- Earlier implementation records remain linked from the documentation index. Their historical feature descriptions are not current release promises.
