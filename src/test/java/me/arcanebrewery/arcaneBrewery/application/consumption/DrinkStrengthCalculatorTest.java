package me.arcanebrewery.arcaneBrewery.application.consumption;

import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DrinkStrengthCalculatorTest {
    @Test
    void moreDistillationPassesIncreaseStrength() {
        var calc = new DrinkStrengthCalculator();
        assertTrue(calc.calculate(process(2)) > calc.calculate(process(1)));
    }

    @Test
    void strengthIsDeterministicAndBounded() {
        var calc = new DrinkStrengthCalculator();
        double a = calc.calculate(process(3));
        double b = calc.calculate(process(3));
        assertEquals(a, b);
        assertTrue(a >= 0 && a <= 100);
    }

    private ProcessProfile process(int passes) {
        return new ProcessProfile(
                Map.of("minecraft:wheat", 1.0),
                "distillation",
                new FermentationProfile("arcanebrewery:wild_yeast", 22, 120, 85, 10),
                new DistillationProfile(10, 80, 10, 78, passes),
                new AgingProfile("oak", 120, 12, 18, 95),
                Map.of()
        );
    }
}
