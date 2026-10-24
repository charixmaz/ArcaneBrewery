package me.arcanebrewery.arcaneBrewery.application.machine;

import java.util.Objects;
import java.util.UUID;

public record MachineIdentity(UUID machineId, Kind kind, UUID ownerId, String ownerName) {
    public enum Kind { EXTRACTOR, CULTIVATION, INFUSER, CASK }
    public MachineIdentity {
        Objects.requireNonNull(machineId); Objects.requireNonNull(kind); Objects.requireNonNull(ownerId);
        ownerName = Objects.requireNonNull(ownerName);
        if (ownerName.length() > 32 || ownerName.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid cached owner name");
    }
    public boolean permits(UUID player, boolean bypass) { return ownerId.equals(player) || bypass; }
    public String displayOwner() { return ownerName.isBlank() ? ownerId.toString() : ownerName; }
}
