# Changelog

Every change must leave a trace here. Entries describe behavior, compatibility, verification and remaining limits; planned work must not be listed as released.

## Unreleased — release polish

### Added
- Permission `arcanebrewery.rename.colors` (default false): color-only `&0–&f` in the one permanent name. No payment integration; owners assign permissions to ranks.
- Persisted cosmetic tasting notes on newly finished bottles. Refresh and specialist processing retain the note.
- Cumulative drawback-duration reduction using Sugar (1s each / 25s cap), Honeycomb (3s / 35s cap) or Amethyst Shard (5s / 45s cap). At least one second remains; benefits and reaction safety remain unchanged.
- Expanded specialist controls with a real processing clock.
- GitHub overview, documentation index, owner guide and contribution/change-record conventions.

### Changed
- Purification consumes ordinary reduction materials instead of a prepared reagent. Historical bottles already fully purified cannot receive this treatment. Existing in-flight operations retain their stored output.

### Verification
- Pending final CI. No live Paper, crate or protection-plugin checks claimed.

### Still pending
- Nine-bottle crates; broader machine GUI redesign and in-game balance testing.

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
