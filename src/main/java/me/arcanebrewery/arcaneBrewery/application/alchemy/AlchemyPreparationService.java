package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Map;
import java.util.Objects;

public final class AlchemyPreparationService {
    public AlchemyPreparation prepare(AlchemyCatalyst catalyst) {
        Objects.requireNonNull(catalyst);
        return new AlchemyPreparation(Map.of(catalyst.additiveId(), catalyst.dose()));
    }
}
