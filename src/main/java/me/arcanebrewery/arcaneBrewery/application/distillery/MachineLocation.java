package me.arcanebrewery.arcaneBrewery.application.distillery;

import java.util.Objects;
import java.util.UUID;

public record MachineLocation(UUID worldId, int x, int y, int z) {
    public MachineLocation {
        Objects.requireNonNull(worldId, "worldId");
    }
}
