# Experimental brewing release phases

1. Candidate deterministic combination-quality policy and balance tests. **Implemented in isolation; not active gameplay.** No new RNG, recipe identity changes, migrations or historical bottle changes.
2. Capture V3 policy on new extraction starts, persist through restart and subsequent machines; freeze all old batches. Integrate mixture-derived cultivation/extraction/maturation targets. Audit all target leaks, automatic ideal resets and starter-perfect walkthroughs. Test complete live pipeline and old/new side by side before activating.
3. Smooth Stone processor foundation with recovery support for old oxidized copper structures; consistent Unnamed Elixir presentation; permission-gated color-only permanent naming; functional machine controls.
4. Purification duration reduction with consumed quantities, selected unwanted effect, Sugar 1s/25s, Honeycomb 3s/35s, Amethyst Shard 5s/45s; cumulative history prevents repeated treatment bypass, minimum 1 second, preserve premature poison rules.
5. Persisted cosmetic tasting notes and nine-bottle crates with no nesting, inventory conservation and crash/restart coverage.

Every phase requires Maven verification, standalone packaging and CI. Paper startup requires owner-provided EULA acceptance. Live GUI, protection-plugin and crash checks must be reported separately. Existing earned unlocks, private recipes, one-time names, owners, unfinished poison and server-first records remain protected. No economy integration.

## Candidate policy

Sorted weighted ingredient properties and a sugar–starch interaction set reachable temperature (2-degree steps), cultivation (30-second steps) and extraction (2-degree steps) targets. Actual upstream deviations influence maturation (15-second steps, 120–600 seconds). Gaussian response curves tolerate nearby settings. A 30-second maturation plateau avoids exact-second requirements; immature quality stays zero and severe over-aging approaches zero. Toxic materials retain a quality penalty. Purity and equipment stability use actual snapshots. Coefficients are provisional balance, not chemistry; broader ingredient/culture sweep tests are required before activation.
