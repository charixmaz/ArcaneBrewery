# Development workflow

1. Work on a focused branch and inspect current behavior before editing.
2. Keep domain calculations independent of Bukkit. Preserve recorded policy versions, metadata and migration checksums.
3. Add meaningful regression coverage for item conservation, persistence, permissions, deterministic outcomes and old/new compatibility.
4. Run Maven verification and standalone packaging; inspect final GitHub CI before merging.
5. Update CHANGELOG.md under Added / Changed / Fixed as applicable. Record actual tests, compatibility effects and anything not implemented.
6. Update the relevant current guide and documentation index. Keep historical implementation notes clearly labeled.

Do not accept a Minecraft EULA automatically, reset worlds/databases, force-push, reintroduce economy payouts or add shared machine ownership. Report live tests as skipped when they were not performed.

Commit examples: `fix: preserve cask output after save failure`, `feat: add persisted tasting notes`, `docs: explain processor migration`. Each PR should state the player-visible problem, resulting behavior, compatibility and validation. Avoid unrelated package reshuffles in gameplay changes.
