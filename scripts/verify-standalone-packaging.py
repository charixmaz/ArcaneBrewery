"""Verify the deployable plugin has no economy API, provider binding or payout path."""
from pathlib import Path
from zipfile import ZipFile
import sys
import xml.etree.ElementTree as ET

def verify(root: Path) -> None:
    jars = list((root / "target").glob("ArcaneBrewery-*.jar"))
    if len(jars) != 1:
        raise ValueError(f"Expected one Brewery JAR, found {jars}")
    forbidden = (
        b"me/arcanebrewery/arcaneeconomy/",
        b"OptionalArcaneEconomy",
        b"ArcaneEconomyConnection",
        b"ArcaneEconomyAdapter",
        b"BukkitMarketPlayerService",
        b"BukkitMarketAssetPort",
        b"MarketSettlementService",
        b"MarketSettlementRuntime",
        b"EconomyPort",
    )
    with ZipFile(jars[0]) as jar:
        for name in jar.namelist():
            if name.endswith(".class"):
                payload = jar.read(name)
                if any(marker in payload for marker in forbidden):
                    raise ValueError(f"Economy integration or payout bytecode remains in {name}")
        descriptor = jar.read("plugin.yml").decode("utf-8")
        if "ArcaneEconomy" in descriptor or "arcanebrewery.market.sell" in descriptor:
            raise ValueError("Economy provider dependency or sale permission remains")
        config = jar.read("config.yml").decode("utf-8")
        if "\nmarket:" in config or "player-sales-enabled" in config:
            raise ValueError("Active market configuration remains")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(root / "pom.xml")
    if any("economy" in (node.text or "").lower() for node in pom.findall(".//m:dependency/m:artifactId", ns)):
        raise ValueError("Economy build dependency remains")
    print("Standalone packaging verified: no economy API, provider binding, sale permission or payout path")

if __name__ == "__main__":
    verify(Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path(__file__).resolve().parent.parent)

