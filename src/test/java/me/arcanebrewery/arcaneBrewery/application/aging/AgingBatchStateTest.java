package me.arcanebrewery.arcaneBrewery.application.aging;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgingBatchStateTest {
    @Test
    void elapsedUsesWallClockAndNeverGoesNegative() {
        var state = new AgingBatchState(AgingBatchState.VERSION, UUID.randomUUID(),
                new AgingSettings("oak", 10, 18, 90), 10_000);
        assertEquals(0, state.elapsedSeconds(9_000));
        assertEquals(30, state.elapsedSeconds(40_000));
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> new AgingSettings("", 10, 18, 90));
        assertThrows(IllegalArgumentException.class, () -> new AgingSettings("oak", 101, 18, 90));
    }
}
