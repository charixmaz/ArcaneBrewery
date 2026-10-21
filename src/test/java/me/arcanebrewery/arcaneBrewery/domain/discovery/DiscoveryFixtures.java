package me.arcanebrewery.arcaneBrewery.domain.discovery;

import java.time.Instant;
import java.util.UUID;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.process;

final class DiscoveryFixtures {
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final Instant DISCOVERED_AT = Instant.parse("2026-10-21T10:00:00Z");

    private DiscoveryFixtures() {}
    static PlayerDiscovery discovery() { return PlayerDiscovery.discover(OWNER, process(), DISCOVERED_AT); }
}
