package me.arcanebrewery.arcaneBrewery.domain.quality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BatchQualityTest {

    @Test
    void contaminationReducesOverallScore() {
        BatchQuality clean = new BatchQuality(90, 85, 80, 90, 0, 20);
        BatchQuality contaminated = new BatchQuality(90, 85, 80, 90, 60, 20);

        assertTrue(clean.overallScore() > contaminated.overallScore());
    }

    @Test
    void clampsValuesToValidRange() {
        BatchQuality quality = new BatchQuality(200, -20, 50, 50, 120, 50);

        assertEquals(100, quality.purity());
        assertEquals(0, quality.flavor());
        assertEquals(100, quality.contamination());
    }
}
