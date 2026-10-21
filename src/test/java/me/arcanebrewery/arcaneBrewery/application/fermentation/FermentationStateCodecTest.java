package me.arcanebrewery.arcaneBrewery.application.fermentation;

import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FermentationStateCodecTest {
    private final FermentationStateCodec codec = new FermentationStateCodec();

    private FermentationBatchState state(FermentationBatchState.Status status) {
        var draft = new DistilleryProcessDraft(
                Map.of("minecraft:wheat", 1.0),
                Map.of("minecraft:wheat", 80.0),
                "distillation",
                DistillationControls.DEFAULT
        );
        return new FermentationBatchState(
                FermentationBatchState.VERSION,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                draft,
                FermentationSettings.DEFAULT,
                1_000,
                status
        );
    }

    @Test
    void roundTripsExactly() {
        var original = state(FermentationBatchState.Status.FERMENTING);
        assertEquals(original, codec.decode(codec.encode(original)));
        assertEquals(original, codec.decodeBase64(codec.encodeBase64(original)));
    }

    @Test
    void timestampControlsOfflineCompletion() {
        var batch = state(FermentationBatchState.Status.FERMENTING);
        long finish = 1_000 + FermentationSettings.DEFAULT.durationSeconds() * 1000L;
        assertFalse(batch.ready(finish - 1));
        assertTrue(batch.ready(finish));
        var completed = batch.complete(finish);
        assertEquals(FermentationBatchState.Status.COMPLETED, completed.status());
        assertEquals(FermentationSettings.DEFAULT.profile(), completed.fermentedDraft().fermentation());
    }

    @Test
    void malformedPayloadFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[]{1,2,3,4}));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBase64("broken"));
    }
}
