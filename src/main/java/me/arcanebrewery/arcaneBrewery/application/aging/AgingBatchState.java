package me.arcanebrewery.arcaneBrewery.application.aging;

import java.util.Objects;
import java.util.UUID;

public record AgingBatchState(
        int formatVersion,
        UUID batchId,
        AgingSettings settings,
        long startedAtEpochMillis
) {
    public static final int VERSION = 1;

    public AgingBatchState {
        if (formatVersion != VERSION) throw new IllegalArgumentException("unsupported aging state version");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(settings, "settings");
        if (startedAtEpochMillis < 0) throw new IllegalArgumentException("invalid aging start");
    }

    public long elapsedSeconds(long nowEpochMillis) {
        if (nowEpochMillis < startedAtEpochMillis) return 0;
        return (nowEpochMillis - startedAtEpochMillis) / 1000L;
    }
}
