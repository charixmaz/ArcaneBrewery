package me.arcanebrewery.arcaneBrewery.application.machine;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineInventoryCodecTest {
    private final MachineInventoryCodec codec=new MachineInventoryCodec();
    private List<byte[]> slots() { return new ArrayList<>(Collections.nCopies(9,new byte[0])); }
    @Test void inventoryAndProcessRoundTripAsOneDeterministicEnvelope() {
        var slots=slots(); slots.set(0,new byte[]{1,2,3}); slots.set(4,new byte[]{40});
        var state=new MachineInventoryCodec.Snapshot("captured-process",slots);
        byte[] encoded=codec.encode(state); var decoded=codec.decode(encoded);
        assertEquals("captured-process",decoded.process()); assertArrayEquals(new byte[]{40},decoded.slots().get(4));
        assertArrayEquals(encoded,codec.encode(decoded));
    }
    @Test void defensiveCopiesProtectCapturedInputs() {
        var slots=slots(); byte[] item={3}; slots.set(0,item); var snapshot=new MachineInventoryCodec.Snapshot("",slots);
        item[0]=99; snapshot.slots().get(0)[0]=100;
        assertArrayEquals(new byte[]{3},snapshot.slots().get(0));
    }
    @Test void corruptionTruncationAndTrailingPayloadsFailClosed() {
        byte[] valid=codec.encode(new MachineInventoryCodec.Snapshot("",slots()));
        byte[] corrupt=valid.clone(); corrupt[4]^=1;
        assertThrows(IllegalArgumentException.class,() -> codec.decode(corrupt));
        assertThrows(IllegalArgumentException.class,() -> codec.decode(Arrays.copyOf(valid,valid.length-1)));
        assertThrows(IllegalArgumentException.class,() -> codec.decode(Arrays.copyOf(valid,valid.length+1)));
    }
    @Test void boundsAndNonAsciiStatesAreRejectedBeforeSaving() {
        assertThrows(IllegalArgumentException.class,() -> new MachineInventoryCodec.Snapshot("é",slots()));
        assertThrows(IllegalArgumentException.class,() -> new MachineInventoryCodec.Snapshot("",List.of(new byte[0])));
        var huge=slots(); huge.set(0,new byte[262_145]);
        assertThrows(IllegalArgumentException.class,() -> new MachineInventoryCodec.Snapshot("",huge));
        assertThrows(IllegalArgumentException.class,() -> codec.decode(new byte[1_048_609]));
    }
}
