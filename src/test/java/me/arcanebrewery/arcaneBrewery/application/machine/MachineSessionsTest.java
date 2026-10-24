package me.arcanebrewery.arcaneBrewery.application.machine;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MachineSessionsTest {
    @Test void oneLeasePerMachineEvenForSamePlayer() {
        var sessions=new MachineSessions(); UUID machine=UUID.randomUUID(),owner=UUID.randomUUID();
        assertTrue(sessions.acquire(machine,owner).isPresent());
        assertTrue(sessions.acquire(machine,owner).isEmpty()); assertTrue(sessions.acquire(machine,UUID.randomUUID()).isEmpty());
    }
    @Test void staleOrWrongPlayerCannotReleaseAnotherSession() {
        var sessions=new MachineSessions(); UUID machine=UUID.randomUUID(),owner=UUID.randomUUID();
        var first=sessions.acquire(machine,owner).orElseThrow(); assertTrue(sessions.release(first));
        var second=sessions.acquire(machine,owner).orElseThrow();
        assertFalse(sessions.release(first)); assertTrue(sessions.owns(second,owner)); assertFalse(sessions.owns(second,UUID.randomUUID()));
        assertFalse(sessions.release(new MachineSessions.Lease(machine,UUID.randomUUID(),second.token())));
    }
    @Test void disconnectAndShutdownReleaseOnlyIntendedLeases() {
        var sessions=new MachineSessions(); UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        var a=sessions.acquire(UUID.randomUUID(),first).orElseThrow(); var b=sessions.acquire(UUID.randomUUID(),second).orElseThrow();
        sessions.releasePlayer(first); assertFalse(sessions.owns(a,first)); assertTrue(sessions.owns(b,second));
        sessions.clear(); assertFalse(sessions.owns(b,second)); assertTrue(sessions.acquire(b.machineId(),first).isPresent());
    }
}
