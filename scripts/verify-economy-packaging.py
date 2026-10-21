"""Verify runtime API ownership after building all three Maven projects (stdlib only)."""
from pathlib import Path
from zipfile import ZipFile
import sys


def verify(root: Path) -> None:
    def artifact(directory: str, pattern: str) -> Path:
        candidates = list((root / directory / "target").glob(pattern))
        if len(candidates) != 1:
            raise ValueError(f"Expected exactly one {pattern} in {directory}/target: {candidates}")
        return candidates[0]

    api_path = artifact("arcane-economy-api", "arcane-economy-api-*.jar")
    economy_path = artifact("arcane-economy", "ArcaneEconomy-*.jar")
    brewery_path = artifact(".", "ArcaneBrewery-*.jar")
    prefix = "me/arcanebrewery/arcaneeconomy/"
    with ZipFile(api_path) as api, ZipFile(economy_path) as economy, ZipFile(brewery_path) as brewery:
        api_classes = {name for name in api.namelist() if name.endswith(".class")}
        if not api_classes or not all(name.startswith(prefix + "api/") for name in api_classes):
            raise ValueError("API artifact contains implementation classes or is empty")
        for name in api_classes:
            if b"org/bukkit/" in api.read(name) or b"java/sql/" in api.read(name):
                raise ValueError(f"API has a Paper/JDBC dependency: {name}")
            if economy.namelist().count(name) != 1 or economy.read(name) != api.read(name):
                raise ValueError(f"Provider does not own exactly the shared API bytecode: {name}")
        if "plugin.yml" in api.namelist():
            raise ValueError("API artifact must not be installed as a Paper plugin")
        if any(name.startswith(prefix) for name in brewery.namelist()):
            raise ValueError("Brewery packaged economy API or implementation classes")
        descriptor = brewery.read("plugin.yml").decode("utf-8")
        if "softdepend: [ArcaneEconomy]" not in descriptor or "\ndepend:" in descriptor:
            raise ValueError("Brewery optional dependency descriptor is incorrect")
        if "me/arcanebrewery/arcaneeconomy/storage/SqliteLedger.class" not in economy.namelist():
            raise ValueError("Provider implementation is missing")
    print(f"Packaging verified: {len(api_classes)} API classes owned once by ArcaneEconomy; none in ArcaneBrewery")


if __name__ == "__main__":
    verify(Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent)
