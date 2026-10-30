# ArcaneAlchemy

Fantasy brewing and alchemy for **Paper 26.1.2 · Java 25**.

Experiment with ingredient proportions, extraction, cultures, catalysts and maturation. Discover private formulations, name them once and improve reproducible elixirs. Brewery runs standalone: no economy provider or resource pack is required.

## Start here

| Reader | Documentation |
|---|---|
| Server owner | [Installation and updates](docs/server-owner.md) |
| Player | [Machines and controls](docs/phase2-specialists-release.md) · `/brew guide` |
| Developer | [Repository map and workflow](CONTRIBUTING.md) |
| Looking for changes | [Changelog](CHANGELOG.md) |
| Looking for all documentation | [Documentation index](docs/README.md) |

## Current gameplay

- Wash → extract → cultivate → optional infusion → mature → inspect and consume.
- Eight obtainable ingredient entries, 26 raw reagents and three prepared reagents.
- Four production machines and four specialists; personal XP unlocks, physical construction, exclusive ownership and sessions.
- V3 mixture-dependent quality, with old batches retaining their captured policy.
- Private discoveries, one permanent personal rename, and persistent server-first recognition.

## Build and deploy

```sh
mvn --batch-mode clean verify
python3 scripts/verify-standalone-packaging.py
```

Back up plugin data, worlds and player data. **Pull → rebuild → replace Brewery JAR → restart Paper.** Use `target/ArcaneAlchemy-1.0-SNAPSHOT.jar`.

Automated CI is not live gameplay certification. Paper startup requires owner-provided EULA acceptance; plugin/protection compatibility and multiplayer crash recovery must be checked on the target server. See the changelog for verification of each change.

## Project layout

| Path | Purpose |
|---|---|
| `src/main/java/.../domain` | Ingredients, formulation identity and quality models |
| `src/main/java/.../application` | Brewing processes and use cases |
| `src/main/java/.../adapter` | Paper inventory, metadata and machine boundaries |
| `src/main/java/.../storage` | Database repositories and migrations |
| `src/main/resources` | Server configuration, messages and permissions |
| `src/test` | Domain, persistence and inventory regression tests |
| `docs` | Current guides and historical implementation notes |
| `scripts` | Packaging and optional Paper checks |

Some older runtime classes remain in the root Java package. Package moves will be done separately from behavior changes to keep review and compatibility manageable.

## Upgrade from ArcaneBrewery

Stop Paper and remove the old Brewery JAR before installing `ArcaneAlchemy-1.0-SNAPSHOT.jar`; never run both. On first startup the legacy `plugins/ArcaneBrewery` data folder is copied into `plugins/ArcaneAlchemy`, retaining the original as a recovery copy. Conflicting populated folders fail closed. Existing database names, item namespaces and permission nodes remain stable for compatibility. `/al` is the short command; `/arcanealchemy` is the full command. `/al guide` gives a reusable Codex book; the codex subcommand has been removed. See [upgrade and shared access](docs/arcanealchemy-upgrade.md).
