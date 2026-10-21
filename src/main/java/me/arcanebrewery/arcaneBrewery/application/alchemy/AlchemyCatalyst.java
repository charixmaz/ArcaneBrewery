package me.arcanebrewery.arcaneBrewery.application.alchemy;

import me.arcanebrewery.arcaneBrewery.domain.process.DomainValues;

import java.util.Objects;

public record AlchemyCatalyst(String id, String additiveId, double dose, String displayName) {
    public AlchemyCatalyst {
        id = DomainValues.id(id);
        additiveId = DomainValues.id(additiveId);
        if (!Double.isFinite(dose) || dose <= 0)
            throw new IllegalArgumentException("catalyst dose must be positive");
        displayName = Objects.requireNonNull(displayName).strip();
        if (displayName.isEmpty()) throw new IllegalArgumentException("display name is blank");
    }
}
