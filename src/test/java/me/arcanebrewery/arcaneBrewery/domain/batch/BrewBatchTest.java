package me.arcanebrewery.arcaneBrewery.domain.batch;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BrewBatchTest {

    @Test
    void normalizesIngredientRatios() {
        BrewBatch batch = new BrewBatch(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Map.of("wheat", 3.0, "honey", 1.0),
                100L
        );

        assertEquals(0.75, batch.ingredientRatios().get("wheat"), 0.000001);
        assertEquals(0.25, batch.ingredientRatios().get("honey"), 0.000001);
    }

    @Test
    void rejectsEmptyIngredients() {
        assertThrows(IllegalArgumentException.class, () ->
                new BrewBatch(UUID.randomUUID(), UUID.randomUUID(), Map.of(), 100L)
        );
    }
}
