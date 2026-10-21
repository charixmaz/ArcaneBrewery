package me.arcanebrewery.arcaneBrewery.application.distillery;

import me.arcanebrewery.arcaneBrewery.application.ingredient.ExtractedIngredients;

import java.util.Objects;
import java.util.UUID;

public final class DistilleryService {
    public DistilleryBatchState start(MachineLocation machine, ExtractedIngredients ingredients,
            DistillationControls controls, long nowEpochMillis, long durationMillis, UUID batchId) {
        Objects.requireNonNull(machine);
        Objects.requireNonNull(ingredients);
        Objects.requireNonNull(controls);
        Objects.requireNonNull(batchId);
        return new DistilleryBatchState(DistilleryBatchState.VERSION, batchId, machine,
                DistilleryProcessDraft.from(ingredients, controls), nowEpochMillis, durationMillis,
                DistilleryBatchState.Status.PROCESSING);
    }
}
