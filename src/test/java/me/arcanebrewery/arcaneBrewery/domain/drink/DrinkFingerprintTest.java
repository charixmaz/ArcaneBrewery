package me.arcanebrewery.arcaneBrewery.domain.drink;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DrinkFingerprintTest {
    @Test
    void identicalProcessesHaveIdenticalFingerprints() {
        assertEquals(DrinkFingerprint.from(process()), DrinkFingerprint.from(process()));
        assertEquals(64, DrinkFingerprint.from(process()).value().length());
    }

    @Test
    void mapOrderCaseWhitespaceAndProportionalAmountsDoNotChangeIdentity() {
        Map<String, Double> ratios = new LinkedHashMap<>();
        ratios.put("  HONEY ", 35.0);
        ratios.put(" Wheat ", 65.0);
        var p = process();
        var equivalent = new ProcessProfile(ratios, " MASH ",
                new FermentationProfile("\u00a0WILD\t Yeast\u00a0", 22, 3600, 100, 0),
                p.distillation(), aging(" OAK ", 7200, 0, 15, 100), Map.of(" MOON   dust ", 0.1));
        assertEquals(DrinkFingerprint.from(p), DrinkFingerprint.from(equivalent));
    }

    @Test
    void additiveMapOrderDoesNotChangeIdentity() {
        var p = process();
        Map<String, Double> a = new LinkedHashMap<>();
        a.put("salt", 1.0); a.put("dust", 2.0);
        Map<String, Double> b = new LinkedHashMap<>();
        b.put("DUST", 2.0); b.put("SALT", 1.0);
        assertEquals(DrinkFingerprint.from(new ProcessProfile(p.ingredientRatios(), "mash",
                        p.fermentation(), p.distillation(), p.aging(), a)),
                DrinkFingerprint.from(new ProcessProfile(p.ingredientRatios(), "mash",
                        p.fermentation(), p.distillation(), p.aging(), b)));
    }

    @Test
    void everyMeaningfulProcessDimensionChangesIdentity() {
        var p = process();
        var changes = List.of(
                new ProcessProfile(Map.of("wheat", 0.6, "honey", 0.4), "mash", p.fermentation(),
                        p.distillation(), p.aging(), p.alchemicalAdditives()),
                new ProcessProfile(p.ingredientRatios(), "boil", p.fermentation(),
                        p.distillation(), p.aging(), p.alchemicalAdditives()),
                withFermentation(fermentation(22.00001, 3600, 100, 0)),
                withFermentation(fermentation(22, 3601, 100, 0)),
                withFermentation(new FermentationProfile("cultured yeast", 22, 3600, 100, 0)),
                withDistillation(distillation(11, 79, 10, 1)),
                withDistillation(distillation(10, 80, 10, 2)),
                withDistillation(new DistillationProfile(10, 80, 10, 79, 1)),
                withAging(aging("birch", 7200, 0, 15, 100)),
                withAging(aging("oak", 7201, 0, 15, 100)),
                new ProcessProfile(p.ingredientRatios(), "mash", p.fermentation(),
                        p.distillation(), p.aging(), Map.of("moon dust", 0.2)));
        for (var changed : changes) assertNotEquals(DrinkFingerprint.from(p), DrinkFingerprint.from(changed));
    }

    @Test
    void batchConditionsDoNotChangeIdentity() {
        assertEquals(DrinkFingerprint.from(process()),
                DrinkFingerprint.from(withFermentation(fermentation(22, 3600, 0, 100))));
        assertEquals(DrinkFingerprint.from(process()),
                DrinkFingerprint.from(withAging(aging("oak", 7200, 90, 50, 0))));
    }

    @Test
    void processMapsAreDefensiveAndImmutable() {
        var input = new LinkedHashMap<>(Map.of("wheat", 1.0));
        var p = process();
        var copy = new ProcessProfile(input, "mash", p.fermentation(), p.distillation(), p.aging(), Map.of());
        input.put("honey", 2.0);
        assertEquals(Map.of("wheat", 1.0), copy.ingredientRatios());
        assertThrows(UnsupportedOperationException.class, () -> copy.ingredientRatios().put("honey", 1.0));
    }
}
