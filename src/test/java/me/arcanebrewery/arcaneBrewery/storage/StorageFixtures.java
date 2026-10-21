package me.arcanebrewery.arcaneBrewery.storage;

import me.arcanebrewery.arcaneBrewery.domain.discovery.PlayerDiscovery;
import java.time.Instant;
import java.util.UUID;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.process;

public final class StorageFixtures {
    public static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final Instant TIME = Instant.parse("2026-10-21T10:00:00.123456789Z");
    private StorageFixtures() {}
    public static PlayerDiscovery discovery() { return PlayerDiscovery.discover(OWNER, process(), TIME); }
}
