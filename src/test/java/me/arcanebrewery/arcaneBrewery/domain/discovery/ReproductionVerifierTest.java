package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import org.junit.jupiter.api.Test;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static me.arcanebrewery.arcaneBrewery.domain.discovery.DiscoveryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ReproductionVerifierTest {
    private final ReproductionVerifier verifier = new ReproductionVerifier();

    @Test
    void sameProcessVerifiesAndMeaningfulChangesFail() {
        var discovery = discovery();
        assertTrue(verifier.reproduces(discovery, process()));
        assertFalse(verifier.reproduces(discovery, withFermentation(fermentation(23, 3600, 100, 0))));
        assertFalse(verifier.reproduces(discovery, withAging(aging("birch", 7200, 0, 15, 100))));
        assertFalse(verifier.reproduces(discovery, withDistillation(distillation(0, 100, 0, 1))));
    }

    @Test
    void qualityConditionsAndVariationAreIrrelevantToReproduction() {
        var calculator = new BatchQualityCalculator();
        var original = process();
        var dirty = withFermentation(fermentation(22, 3600, 0, 100));
        var pristineBatch = calculator.calculate(original, TARGETS, clean(), BatchVariation.fromSeed(42));
        var contaminatedBatch = calculator.calculate(dirty, TARGETS, conditions(80, 30, 20, WOOD),
                BatchVariation.fromSeed(43));
        assertNotEquals(pristineBatch.quality(), contaminatedBatch.quality());
        assertEquals(pristineBatch.fingerprint(), contaminatedBatch.fingerprint());
        assertTrue(verifier.reproduces(discovery(), dirty));
    }

    @Test
    void namesVisibilityAndSharingCannotChangeReproduction() {
        var discovery = discovery().rename(OWNER, "Presentation Only").shareFull(OWNER, FRIEND)
                .changeVisibility(OWNER, DiscoveryVisibility.PUBLIC);
        assertTrue(verifier.reproduces(discovery, process()));
    }

    @Test
    void nullCandidatesOrDiscoveriesAreRejected() {
        assertThrows(NullPointerException.class, () -> verifier.reproduces(discovery(), null));
        assertThrows(NullPointerException.class, () -> verifier.reproduces(null, process()));
    }
}
