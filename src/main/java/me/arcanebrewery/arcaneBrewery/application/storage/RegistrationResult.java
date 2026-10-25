package me.arcanebrewery.arcaneBrewery.application.storage;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.Objects;
import java.util.UUID;

/** V1 reports the exclusive original owner; V2 reports server-first recognition, never secret process data. */
public record RegistrationResult(Status status, DrinkFingerprint fingerprint, UUID originalDiscoverer) {
    public enum Status { REGISTERED, ALREADY_DISCOVERED }
    public RegistrationResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(originalDiscoverer);
    }
}
