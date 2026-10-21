package me.arcanebrewery.arcaneBrewery.application.distillery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistillationControlsTest {
    @Test
    void defaultCutsAreValid() {
        assertEquals(100.0, DistillationControls.DEFAULT.headsCutPercent()
                + DistillationControls.DEFAULT.heartsCutPercent()
                + DistillationControls.DEFAULT.tailsCutPercent(), 0.0001);
    }

    @Test
    void rejectsImpossibleCuts() {
        assertThrows(IllegalArgumentException.class, () -> new DistillationControls(78, 20, 80, 10, 1));
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException.class, () -> new DistillationControls(Double.NaN, 10, 80, 10, 1));
    }
}
