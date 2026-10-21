package me.arcanebrewery.arcaneBrewery.application.economy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.BatchQuality;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DrinkValueCalculatorTest {
    private final DrinkValueCalculator calculator = new DrinkValueCalculator();

    @Test
    void valuationIsDeterministicForSameBatchInputs() {
        var process = process(1, 86_400, Map.of());
        var quality = quality(80, 5);
        var alchemy = new AlchemyResult(AlchemyEffect.NONE, 0, 0, 0, false);

        assertEquals(
                calculator.calculate(process, quality, alchemy),
                calculator.calculate(process, quality, alchemy)
        );
    }

    @Test
    void higherQualityRaisesValue() {
        var process = process(1, 86_400, Map.of());
        var alchemy = new AlchemyResult(AlchemyEffect.NONE, 0, 0, 0, false);

        double low = calculator.calculate(process, quality(30, 5), alchemy).units();
        double high = calculator.calculate(process, quality(90, 5), alchemy).units();

        assertTrue(high > low);
    }

    @Test
    void contaminationReducesValue() {
        var process = process(1, 86_400, Map.of());
        var alchemy = new AlchemyResult(AlchemyEffect.NONE, 0, 0, 0, false);

        double clean = calculator.calculate(process, quality(80, 0), alchemy).units();
        double dirty = calculator.calculate(process, quality(80, 80), alchemy).units();

        assertTrue(clean > dirty);
    }

    @Test
    void processComplexityAndAgingCanRaiseValue() {
        var alchemy = new AlchemyResult(AlchemyEffect.NONE, 0, 0, 0, false);
        var quality = quality(80, 5);

        double simple = calculator.calculate(process(0, 0, Map.of()), quality, alchemy).units();
        double complex = calculator.calculate(
                process(3, 30L * 86_400L, Map.of("arcanebrewery:fire_catalyst", 1.0)),
                quality, alchemy).units();

        assertTrue(complex > simple);
    }

    @Test
    void magicalEffectAddsDeterministicPremium() {
        var process = process(1, 86_400, Map.of("arcanebrewery:teleport_catalyst", 1.0));
        var quality = quality(80, 5);

        double mundane = calculator.calculate(
                process, quality, new AlchemyResult(AlchemyEffect.NONE, 0, 0, 0, false)).units();
        double magical = calculator.calculate(
                process, quality, new AlchemyResult(AlchemyEffect.TELEPORTATION, 80, 75, 20, false)).units();

        assertTrue(magical > mundane);
    }

    private static ProcessProfile process(int passes, long agingSeconds, Map<String, Double> additives) {
        return new ProcessProfile(
                Map.of("minecraft:wheat", 1.0),
                "distillation",
                new FermentationProfile("arcanebrewery:wild_yeast", 22, 600, 90, 10),
                new DistillationProfile(10, 80, 10, 78, passes),
                new AgingProfile("oak", agingSeconds, 10, 18, 95),
                additives
        );
    }

    private static BatchQuality quality(double positive, double contamination) {
        return new BatchQuality(
                positive, positive, positive, positive, contamination, positive * 0.5);
    }
}
