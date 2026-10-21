package me.arcanebrewery.arcaneBrewery.domain.drink;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DrinkFingerprintTest {

    @Test
    void sameProcessProducesSameFingerprintRegardlessOfIngredientMapOrder() {
        Map<String, Double> first = new LinkedHashMap<>();
        first.put("wheat", 0.65);
        first.put("honey", 0.35);

        Map<String, Double> second = new LinkedHashMap<>();
        second.put("honey", 0.35);
        second.put("wheat", 0.65);

        DrinkFingerprint a = DrinkFingerprint.from(first, "mash", "wild_yeast", 22.5, 3600, "10/80/10", "oak", 7200, "");
        DrinkFingerprint b = DrinkFingerprint.from(second, "mash", "wild_yeast", 22.5, 3600, "10/80/10", "oak", 7200, "");

        assertEquals(a, b);
        assertEquals(64, a.value().length());
    }

    @Test
    void meaningfulProcessChangeChangesFingerprint() {
        Map<String, Double> ingredients = Map.of("wheat", 1.0);

        DrinkFingerprint a = DrinkFingerprint.from(ingredients, "mash", "yeast_a", 20.0, 3600, "10/80/10", "oak", 7200, "");
        DrinkFingerprint b = DrinkFingerprint.from(ingredients, "mash", "yeast_a", 24.0, 3600, "10/80/10", "oak", 7200, "");

        assertNotEquals(a, b);
    }
}
