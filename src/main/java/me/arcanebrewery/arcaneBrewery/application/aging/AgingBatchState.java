package me.arcanebrewery.arcaneBrewery.application.aging;

import java.util.Objects;
import java.util.UUID;

public record AgingBatchState(
        int formatVersion,
        UUID batchId,
        AgingSettings settings,
        long startedAtEpochMillis,
        long maturationSeconds
) {
    public static final int VERSION = 1;
    public AgingBatchState(int version, UUID id, AgingSettings settings, long started) {
        this(version,id,settings,started,120L);
    }

    public AgingBatchState {
        if (formatVersion != VERSION) throw new IllegalArgumentException("unsupported aging state version");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(settings, "settings");
        if (startedAtEpochMillis < 0) throw new IllegalArgumentException("invalid aging start");
        if (maturationSeconds < 1 || maturationSeconds > 604_800) throw new IllegalArgumentException("invalid maturation duration");
    }

    public long elapsedSeconds(long nowEpochMillis) {
        if (nowEpochMillis < startedAtEpochMillis) return 0;
        return (nowEpochMillis - startedAtEpochMillis) / 1000L;
    }
}
