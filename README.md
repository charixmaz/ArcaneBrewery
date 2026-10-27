# ArcaneBrewery — Brewing & Alchemy

A standalone fantasy brewing and alchemy plugin for **Paper 26.1.2 / Java 25**.

Wash supported ingredients, extract refined bases, cultivate them, optionally
infuse catalysts, then mature and discover elixirs. Exact process determines
recipe identity; batch variation affects quality rather than identity.

Use `/brew help` for short topics: Getting Started, Machines, Recipes
and Commands. Discoveries remain private by default. Temporary Arcane Saturation,
Overload and recovery are the default player conditions; historical alcohol
progression is opt-in.

The four ordinary production machines and four researched specialists have persistent ownership and exclusive access.
No resource pack or economy provider is required. Optional sales remain disabled
by default.

For this update, the existing deployment workflow remains:

**Git Pull → Rebuild Project → replace ArcaneBrewery JAR → restart Paper.**

Build Brewery with `mvn --batch-mode clean verify`. This update does not change
or require rebuilding the already installed economy API contract.

See [fantasy defaults, ownership, configuration and compatibility](docs/fantasy-machines.md)
and [the local Paper checklist](docs/local-playtest.md).

Current closed-beta controls and migration: [Brewing gameplay V2](docs/brewing-gameplay-v2.md).

Material exploration and typed effects: [Discovery and alchemy Phase 1](docs/discovery-alchemy-phase1.md).

Phase 2 release, exact research/build costs, specialist caps and deployment: [Personal research and specialists](docs/phase2-specialists-release.md).
