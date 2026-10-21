package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Objects;
import java.util.UUID;

public record AlchemyMachineState(
        int version,
        UUID batchId,
        long startedAtEpochMillis,
        long durationMillis,
        Status status
) {
    public static final int VERSION = 1;

    public enum Status { PROCESSING, COMPLETED }

    public AlchemyMachineState {
        if (version != VERSION) throw new IllegalArgumentException("unsupported alchemy machine state version");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(status, "status");
        if (startedAtEpochMillis < 0 || durationMillis <= 0)
            throw new IllegalArgumentException("invalid alchemy timing");
    }

    public boolean ready(long nowEpochMillis) {
        return nowEpochMillis >= startedAtEpochMillis + durationMillis;
    }

    public AlchemyMachineState complete(long nowEpochMillis) {
        if (status == Status.COMPLETED) return this;
        if (!ready(nowEpochMillis)) throw new IllegalStateException("alchemy batch is not ready");
        return new AlchemyMachineState(version, batchId, startedAtEpochMillis, durationMillis, Status.COMPLETED);
    }
}
