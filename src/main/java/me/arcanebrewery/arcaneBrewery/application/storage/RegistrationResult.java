package me.arcanebrewery.arcaneBrewery.application.storage;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.Objects;
import java.util.UUID;

/** A losing registrant learns the original owner, never the stored process or grants. */
public record RegistrationResult(Status status, DrinkFingerprint fingerprint, UUID originalDiscoverer) {
    public enum Status { REGISTERED, ALREADY_DISCOVERED }
    public RegistrationResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(fingerprint);
        Objects.requireNonNull(originalDiscoverer);
    }
}
