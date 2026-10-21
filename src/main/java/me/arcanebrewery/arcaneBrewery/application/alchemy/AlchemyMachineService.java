package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.*;

public final class AlchemyMachineService {
    public AlchemyPreparation combine(List<AlchemyCatalyst> catalysts) {
        Objects.requireNonNull(catalysts, "catalysts");
        if (catalysts.isEmpty()) throw new IllegalArgumentException("at least one catalyst is required");
        if (catalysts.size() > 2) throw new IllegalArgumentException("V1.15 supports at most two catalysts");

        var additives = new TreeMap<String, Double>();
        for (AlchemyCatalyst catalyst : catalysts) {
            Objects.requireNonNull(catalyst, "catalyst");
            additives.merge(catalyst.additiveId(), catalyst.dose(), Double::sum);
        }
        return new AlchemyPreparation(additives);
    }

    public double previewInstability(List<AlchemyCatalyst> catalysts) {
        Objects.requireNonNull(catalysts, "catalysts");
        if (catalysts.isEmpty()) return 0;
        double load = catalysts.stream().mapToDouble(AlchemyCatalyst::dose).sum();
        double diversity = catalysts.stream().map(AlchemyCatalyst::additiveId).distinct().count();
        return Math.min(100.0, 12.5 * load + 15.0 * Math.max(0, diversity - 1));
    }

    public AlchemyMachineState start(long nowEpochMillis, long durationMillis, UUID batchId) {
        return new AlchemyMachineState(AlchemyMachineState.VERSION, batchId, nowEpochMillis,
                durationMillis, AlchemyMachineState.Status.PROCESSING);
    }
}
