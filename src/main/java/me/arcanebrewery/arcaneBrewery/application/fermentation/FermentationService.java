package me.arcanebrewery.arcaneBrewery.application.fermentation;

import me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryProcessDraft;

import java.util.Objects;
import java.util.UUID;

public final class FermentationService {
    public FermentationBatchState start(UUID batchId, DistilleryProcessDraft distillery,
            FermentationSettings settings, long nowEpochMillis) {
        return new FermentationBatchState(FermentationBatchState.VERSION,
                Objects.requireNonNull(batchId), Objects.requireNonNull(distillery),
                Objects.requireNonNull(settings), nowEpochMillis,
                FermentationBatchState.Status.FERMENTING);
    }
}
