package me.arcanebrewery.arcaneBrewery.application.distillery;

import java.util.Objects;
import java.util.UUID;

public record DistilleryBatchState(
        int formatVersion,
        UUID batchId,
        MachineLocation machine,
        DistilleryProcessDraft draft,
        long startedAtEpochMillis,
        long durationMillis,
        Status status
) {
    public static final int VERSION = 1;
    public enum Status { PROCESSING, COMPLETED }

    public DistilleryBatchState {
        if (formatVersion != VERSION) throw new IllegalArgumentException("unsupported distillery state version");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(status, "status");
        if (startedAtEpochMillis < 0 || durationMillis <= 0) throw new IllegalArgumentException("invalid distillery timing");
    }

    public boolean ready(long nowEpochMillis) {
        return status == Status.PROCESSING && nowEpochMillis >= Math.addExact(startedAtEpochMillis, durationMillis);
    }

    public DistilleryBatchState complete(long nowEpochMillis) {
        if (status == Status.COMPLETED) return this;
        if (!ready(nowEpochMillis)) throw new IllegalStateException("batch is not ready");
        return new DistilleryBatchState(formatVersion, batchId, machine, draft, startedAtEpochMillis, durationMillis, Status.COMPLETED);
    }
}
