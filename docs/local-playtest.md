> Current release: [Phase 2 specialists](phase2-specialists-release.md) supersedes earlier sharing, repeatable naming, contamination and roadmap-only specialist descriptions below.

# Local Paper playtest

Target: Paper 26.1.2, Java 25. Install only the rebuilt ArcaneBrewery JAR for the
Brewery-only check. The economy API and built-in selling were removed from this
update.

The normal deployment remains **Git Pull → Rebuild Project → replace
ArcaneBrewery JAR → restart Paper**. For a separate disposable Windows server,
`scripts/setup-local-playtest.ps1` verifies/builds Brewery and prepares a test
folder. It writes `eula=false`: the server owner must review and accept the EULA
before starting it. Do not point the helper at a production world.

The automated smoke script also refuses a new implicit EULA acceptance. An
already accepted `.paper-smoke/eula.txt` or explicitly owner-configured
`PAPER_EULA_ACCEPTED=true` is required. A skipped/preflight-blocked smoke test is
not a successful Paper startup test.

## Ordered human checks

1. Restart and confirm Brewery enables; run `/brew help` and its five topics,
   `/brew status`, `/brew discoveries` and an owned short recipe ID. Existing
   custom messages should survive the message migration.
2. Wash a supported ingredient. Use the Brewing Stand above a Crafting Table,
   insert one bottle per input and a washed ingredient; verify automatic start,
   locked inputs, progress and Refined Brewing Base outputs.
3. Put bases into a plain Barrel and close it. Verify ownership, cultivation,
   captured duration and completion after reconnect/restart. Ordinary unrelated
   barrels/stands/hoppers must remain normal.
4. Build the Infuser structure and use a cultivated base with one/two catalysts.
   Verify clear controls, exactly-once consumption, unsafe-reaction behavior and
   progress after closing/reopening.
5. Build the cask from the documented structure and `[Brewery]` sign. Verify four
   readable lanes, unfinished early withdrawal without discovery, and mature
   withdrawal with normal discovery registration. Compare equivalent 121s/126s
   default batches and catalyst/no-catalyst fingerprints.
6. Try a second player and an admin bypass user. Deny non-owner access, allow
   sequential bypass without transferring ownership, deny competing views, and
   release sessions on close/disconnect/chunk unload/restart.
7. Try shift-click, number keys, offhand swaps, double-click, dragging and creative
   actions. Controls must never become loot; item totals must remain conserved.
   Test a full player inventory and ordinary retrieval of preserved legacy items.
8. Test hopper insertion/extraction, core/support breaking, explosions and piston
   movement around claimed machines. Contents/progress must survive refusal or
   repair; removing a component must not finish/reset the batch.
9. Change durations and restart with active batches. Old batches must retain their
   timing; new ones must use the new configuration. Test invalid values on a
   backed-up disposable instance.
10. Use existing bottles and saved player state. Confirm Mana Charged, Mana Overload,
    Mana Depletion and offline recovery. Ensure unrelated potion effects remain intact.
    Withdraw unsupported old cask inputs unchanged. As an administrator, recover old
    producer contents; verify that insertion and conversion are disabled.

Record console exceptions, exact player actions and item counts. Test abrupt
termination separately from graceful restart, with backups. See
[current conditions and upgrade details](mana-conditions.md).
