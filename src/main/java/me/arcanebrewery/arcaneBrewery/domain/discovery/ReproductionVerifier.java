package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.util.Objects;

/** Identity only: no quality, variation, presentation name or player ownership comparison. */
public final class ReproductionVerifier {
    public boolean reproduces(PlayerDiscovery discovery, ProcessProfile candidate) {
        Objects.requireNonNull(discovery, "discovery");
        Objects.requireNonNull(candidate,"candidate");
        boolean v2=discovery.fingerprint().equals(me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity.from(discovery.process()));
        return discovery.fingerprint().equals(v2?me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity.from(candidate):DrinkFingerprint.from(candidate));
    }
}
