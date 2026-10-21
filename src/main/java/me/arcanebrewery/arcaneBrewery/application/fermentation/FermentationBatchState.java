package me.arcanebrewery.arcaneBrewery.application.fermentation;

import me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryProcessDraft;

import java.util.Objects;
import java.util.UUID;

public record FermentationBatchState(
        int formatVersion,
        UUID batchId,
        DistilleryProcessDraft distillery,
        FermentationSettings settings,
        long startedAtEpochMillis,
        Status status
) {
    public static final int VERSION = 1;
    public enum Status { FERMENTING, COMPLETED }

    public FermentationBatchState {
        if (formatVersion != VERSION) throw new IllegalArgumentException("unsupported fermentation state version");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(distillery, "distillery");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(status, "status");
        if (startedAtEpochMillis < 0) throw new IllegalArgumentException("invalid fermentation start");
    }

    public boolean ready(long nowEpochMillis) {
        return status == Status.FERMENTING
                && nowEpochMillis >= Math.addExact(startedAtEpochMillis, Math.multiplyExact(settings.durationSeconds(), 1000L));
    }

    public FermentationBatchState complete(long nowEpochMillis) {
        if (status == Status.COMPLETED) return this;
        if (!ready(nowEpochMillis)) throw new IllegalStateException("fermentation is not ready");
        return new FermentationBatchState(formatVersion, batchId, distillery, settings, startedAtEpochMillis, Status.COMPLETED);
    }

    public FermentedProcessDraft fermentedDraft() {
        if (status != Status.COMPLETED) throw new IllegalStateException("fermentation is incomplete");
        return new FermentedProcessDraft(distillery, settings.profile());
    }
}
