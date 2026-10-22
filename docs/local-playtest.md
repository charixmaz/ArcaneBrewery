# Local Paper playtest

This is the human-interaction test environment for ArcaneBrewery. It is intentionally disposable.

## Prepare the server on Windows

From the repository root:

\`\`\`powershell
powershell -ExecutionPolicy Bypass -File .\scripts\setup-local-playtest.ps1
\`\`\`

The script:

- verifies Java and Maven are available;
- installs the retained ArcaneEconomy API contract locally for compilation;
- runs the ArcaneBrewery Maven verification suite;
- downloads a stable Paper 26.1.2 server;
- creates a fresh server under \`%TEMP%\ArcaneBrewery-Playtest\`;
- installs only ArcaneBrewery;
- enables a disposable offline-mode Creative test server;
- writes a \`run.bat\` launcher.

Start the generated \`run.bat\`, then join \`localhost\`.

Do not point this setup at an existing production world. If the target folder already exists, the script moves it aside to a timestamped backup first.

## First human sanity pass

Check only the interactions automation cannot judge well:

1. Paper reaches Done and ArcaneBrewery reports enabled.
2. \`/brew help\` and \`/brewmarket help\` register.
3. Ingredient washing reacts to a water cauldron.
4. Distillery interaction opens its GUI.
5. Canonical distilled output can enter fermentation.
6. Alchemy multiblock recognizes its physical structure.
7. Aging cask sign/structure opens its GUI.
8. GUI slots cannot be trivially duplicated or stolen.
9. Progress/status text is understandable.
10. Stop and restart the server; confirm saved plugin data still loads.

Record any console exception and the exact player action that triggered it.
