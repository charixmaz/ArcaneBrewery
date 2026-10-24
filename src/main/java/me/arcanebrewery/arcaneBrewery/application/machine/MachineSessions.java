package me.arcanebrewery.arcaneBrewery.application.machine;

import java.util.*;

/** Server-thread scoped, token-based leases. Bypass never bypasses exclusivity. */
public final class MachineSessions {
    public record Lease(UUID machineId, UUID playerId, UUID token) { }
    private final Map<UUID,Lease> active = new HashMap<>();
    public Optional<Lease> acquire(UUID machine, UUID player) {
        Objects.requireNonNull(machine); Objects.requireNonNull(player);
        if (active.containsKey(machine)) return Optional.empty();
        var lease = new Lease(machine,player,UUID.randomUUID()); active.put(machine,lease);
        return Optional.of(lease);
    }
    public boolean release(Lease lease) { return active.remove(lease.machineId(),lease); }
    public boolean owns(Lease lease, UUID player) { return lease.equals(active.get(lease.machineId())) && lease.playerId().equals(player); }
    public void releasePlayer(UUID player) { active.values().removeIf(lease -> lease.playerId().equals(player)); }
    public void clear() { active.clear(); }
}
