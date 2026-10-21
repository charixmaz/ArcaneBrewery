package me.arcanebrewery.arcaneBrewery.application.distillery;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DistilleryStateCodecTest {
    private final DistilleryStateCodec codec = new DistilleryStateCodec();

    private DistilleryBatchState state(long started, DistilleryBatchState.Status status) {
        var draft = new DistilleryProcessDraft(
                Map.of("minecraft:wheat", 0.75, "minecraft:honeycomb", 0.25),
                Map.of("minecraft:wheat", 80.0, "minecraft:honeycomb", 60.0),
                "distillation",
                new DistillationControls(79.5, 10, 80, 10, 2)
        );
        return new DistilleryBatchState(DistilleryBatchState.VERSION,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                new MachineLocation(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), 10, 64, -4),
                draft, started, 10_000, status);
    }

    @Test
    void roundTripsExactly() {
        var original = state(1_000, DistilleryBatchState.Status.PROCESSING);
        assertEquals(original, codec.decode(codec.encode(original)));
        assertEquals(original, codec.decodeBase64(codec.encodeBase64(original)));
    }

    @Test
    void offlineTimeControlsReadiness() {
        var batch = state(1_000, DistilleryBatchState.Status.PROCESSING);
        assertFalse(batch.ready(10_999));
        assertTrue(batch.ready(11_000));
        var completed = batch.complete(11_000);
        assertEquals(DistilleryBatchState.Status.COMPLETED, completed.status());
        assertSame(completed, completed.complete(20_000));
    }

    @Test
    void malformedPayloadFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[]{1,2,3,4}));
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBase64("not base64!"));
    }

    @Test
    void machineIdentityIsStableValueData() {
        UUID world = UUID.randomUUID();
        assertEquals(new MachineLocation(world, 1, 2, 3), new MachineLocation(world, 1, 2, 3));
        assertNotEquals(new MachineLocation(world, 1, 2, 3), new MachineLocation(world, 1, 2, 4));
    }
}
