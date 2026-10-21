package me.arcanebrewery.arcaneBrewery.domain.drink;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DrinkFingerprintStressTest {
    @Test
    void twentyThousandGeneratedProcessesAreDeterministicAndCollisionFree() {
        var seen = new HashMap<String, ProcessProfile>();
        var random = new Random(0xA11CEBEEFL);

        for (int i = 0; i < 20_000; i++) {
            ProcessProfile process = generated(random, i);
            String first = DrinkFingerprint.from(process).value();
            String second = DrinkFingerprint.from(process).value();

            assertEquals(first, second, "same process must always hash identically");
            assertEquals(64, first.length());

            ProcessProfile previous = seen.putIfAbsent(first, process);
            if (previous != null) {
                assertEquals(previous, process, "distinct processes must not collide in stress sample");
            }
        }

        assertEquals(20_000, seen.size());
    }

    @Test
    void proportionalIngredientAmountsRemainStableAcrossLargeRange() {
        for (int scale = 1; scale <= 10_000; scale += 37) {
            var a = profile(Map.of("wheat", 65.0, "honey", 35.0));
            var b = profile(Map.of("wheat", 65.0 * scale, "honey", 35.0 * scale));
            assertEquals(DrinkFingerprint.from(a), DrinkFingerprint.from(b));
        }
    }

    private static ProcessProfile generated(Random random, int i) {
        double wheat = 1 + random.nextInt(100);
        double honey = 1 + random.nextInt(100);
        double berry = 1 + random.nextInt(100);
        Map<String, Double> ingredients = switch (i % 3) {
            case 0 -> Map.of("wheat", wheat, "honey", honey);
            case 1 -> Map.of("wheat", wheat, "sweet berries", berry);
            default -> Map.of("honey", honey, "sweet berries", berry);
        };

        var fermentation = new FermentationProfile(
                "yeast-" + (i % 7),
                15 + (i % 20),
                300L + (long) i * 7L,
                i % 101,
                (i * 3) % 101
        );
        int heads = i % 21;
        int tails = (i * 2) % 21;
        int hearts = 100 - heads - tails;
        var distillation = new DistillationProfile(
                heads, hearts, tails, 70 + (i % 20), 1 + (i % 4)
        );
        var aging = new AgingProfile(
                switch (i % 4) { case 0 -> "oak"; case 1 -> "birch"; case 2 -> "spruce"; default -> "cherry"; },
                600L + (long) i * 11L,
                i % 101,
                10 + (i % 20),
                (i * 5) % 101
        );
        Map<String, Double> additives = (i % 5 == 0)
                ? Map.of("ember", 1.0 + (i % 3))
                : Map.of();

        return new ProcessProfile(ingredients, "prep-" + (i % 5),
                fermentation, distillation, aging, additives);
    }

    private static ProcessProfile profile(Map<String, Double> ingredients) {
        return new ProcessProfile(
                ingredients, "mash",
                new FermentationProfile("wild yeast", 22, 3600, 100, 0),
                new DistillationProfile(10, 80, 10, 78, 1),
                new AgingProfile("oak", 7200, 0, 15, 100),
                Map.of("moon dust", 0.1)
        );
    }
}
