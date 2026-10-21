package me.arcanebrewery.arcaneBrewery.application.aging;

import me.arcanebrewery.arcaneBrewery.domain.quality.QualityTargets;
import me.arcanebrewery.arcaneBrewery.domain.quality.WoodCharacteristics;

import java.util.Locale;

public final class AgingBalance {
    private AgingBalance() {}

    public static QualityTargets targets() {
        return new QualityTargets(22.0, 60L, 120L, 18.0, 12.0);
    }

    public static WoodCharacteristics wood(String woodType) {
        String wood = woodType.toLowerCase(Locale.ROOT);
        return switch (wood) {
            case "oak" -> new WoodCharacteristics(90, 75, 70);
            case "dark_oak" -> new WoodCharacteristics(88, 85, 78);
            case "spruce" -> new WoodCharacteristics(82, 62, 82);
            case "birch" -> new WoodCharacteristics(78, 58, 68);
            case "acacia" -> new WoodCharacteristics(76, 72, 60);
            case "jungle" -> new WoodCharacteristics(72, 68, 62);
            case "mangrove" -> new WoodCharacteristics(84, 80, 76);
            case "cherry" -> new WoodCharacteristics(80, 88, 64);
            case "pale_oak" -> new WoodCharacteristics(86, 72, 84);
            case "crimson" -> new WoodCharacteristics(70, 92, 55);
            case "warped" -> new WoodCharacteristics(68, 90, 58);
            default -> new WoodCharacteristics(60, 50, 50);
        };
    }
}
