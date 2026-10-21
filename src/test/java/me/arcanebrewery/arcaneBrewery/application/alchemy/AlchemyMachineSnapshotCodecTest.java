package me.arcanebrewery.arcaneBrewery.application.alchemy;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyMachineSnapshotCodecTest {
    private final AlchemyMachineSnapshotCodec codec = new AlchemyMachineSnapshotCodec();
    private AlchemyMachineSnapshot snapshot() {
        var service = new AlchemyMachineService();
        var operation = new AlchemyMachineOperation(service.start(1000, 8000, new UUID(0, 1)), new UUID(0, 2),
                service.combine(List.of(BuiltInAlchemyCatalysts.resolveExternal("minecraft:blaze_powder").orElseThrow())));
        return new AlchemyMachineSnapshot(Optional.of(operation), List.of(new byte[]{1,2}, new byte[]{3}, new byte[0]));
    }
    @Test void roundTripPreservesOperationPlanAndAllSlots() {
        var original = snapshot();
        var decoded = codec.decode(codec.encode(original));
        assertEquals(original.operation(), decoded.operation());
        for (int i = 0; i < 3; i++) assertArrayEquals(original.items().get(i), decoded.items().get(i));
        assertArrayEquals(codec.encode(original), codec.encode(decoded));
    }
    @Test void idleAndCompletedSnapshotsRoundTrip() {
        var idle = new AlchemyMachineSnapshot(Optional.empty(), List.of(new byte[0], new byte[0], new byte[0]));
        assertTrue(codec.decode(codec.encode(idle)).operation().isEmpty());
        var completed = new AlchemyMachineSnapshot(Optional.of(snapshot().operation().orElseThrow().complete(9000)), snapshot().items());
        assertEquals(AlchemyMachineState.Status.COMPLETED, codec.decode(codec.encode(completed)).operation().orElseThrow().state().status());
    }
    @Test void snapshotDoesNotExposeMutableArrays() {
        byte[] source = {1};
        var snapshot = new AlchemyMachineSnapshot(Optional.empty(), List.of(source, new byte[0], new byte[0]));
        source[0] = 8;
        snapshot.items().getFirst()[0] = 9;
        assertEquals(1, snapshot.items().getFirst()[0]);
    }
    @Test void corruptionTruncationAndOversizeFailClosed() {
        byte[] corrupt = codec.encode(snapshot()); corrupt[12] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> codec.decode(corrupt));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[AlchemyMachineSnapshotCodec.MAX_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> new AlchemyMachineSnapshot(Optional.empty(),
                List.of(new byte[262145], new byte[0], new byte[0])));
    }
    @Test void unsupportedVersionAndInvalidLengthsAreRejectedEvenWithValidChecksum() throws Exception {
        var idle = new AlchemyMachineSnapshot(Optional.empty(), List.of(new byte[0], new byte[0], new byte[0]));
        byte[] bytes = codec.encode(idle);
        ByteBuffer.wrap(bytes).putInt(4, 99); resign(bytes);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(bytes));
        byte[] invalidLength = codec.encode(idle);
        ByteBuffer.wrap(invalidLength).putInt(9, Integer.MAX_VALUE); resign(invalidLength);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidLength));
        byte[] invalidMarker = codec.encode(idle); invalidMarker[8] = 2; resign(invalidMarker);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(invalidMarker));
    }
    @Test void overflowAndUnsupportedCatalystCountsCannotStartAnOperation() {
        var service = new AlchemyMachineService();
        assertThrows(ArithmeticException.class, () -> service.start(Long.MAX_VALUE, 8000, UUID.randomUUID()));
        var catalyst = BuiltInAlchemyCatalysts.resolveExternal("minecraft:blaze_powder").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> service.previewInstability(List.of(catalyst, catalyst, catalyst)));
        assertThrows(IllegalArgumentException.class, () -> new AlchemyMachineOperation(
                new AlchemyMachineState(1, UUID.randomUUID(), Long.MAX_VALUE, 10, AlchemyMachineState.Status.PROCESSING),
                UUID.randomUUID(), new AlchemyPreparation(Map.of("fire", 1.0))));
    }
    private void resign(byte[] payload) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(payload, payload.length - 32));
        System.arraycopy(hash, 0, payload, payload.length - 32, 32);
    }
}
