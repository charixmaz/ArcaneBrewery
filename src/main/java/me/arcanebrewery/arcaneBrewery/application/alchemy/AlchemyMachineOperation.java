package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Objects;
import java.util.UUID;

/** The validated start plan is captured with the locked inventory, never recomputed from mutable GUI state. */
public record AlchemyMachineOperation(AlchemyMachineState state, UUID sourceBatch, AlchemyPreparation preparation) {
    public AlchemyMachineOperation {
        Objects.requireNonNull(state); Objects.requireNonNull(sourceBatch); Objects.requireNonNull(preparation);
        try { Math.addExact(state.startedAtEpochMillis(), state.durationMillis()); }
        catch (ArithmeticException overflow) { throw new IllegalArgumentException("invalid machine finish timestamp"); }
    }
    public AlchemyMachineOperation complete(long now) {
        return new AlchemyMachineOperation(state.complete(now), sourceBatch, preparation);
    }
}
