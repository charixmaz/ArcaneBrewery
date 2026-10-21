package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.*;

public final class AlchemyMachineService {
    private final AlchemyBalanceProfile balance;
    private final AlchemyFailureModel failureModel;

    public AlchemyMachineService() {
        this(AlchemyBalanceProfile.DEFAULT);
    }

    public AlchemyMachineService(AlchemyBalanceProfile balance) {
        this.balance = Objects.requireNonNull(balance);
        this.failureModel = new AlchemyFailureModel(balance);
    }

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
        return assess(catalysts, AlchemyMachineCondition.PRISTINE).instability();
    }

    public AlchemyFailureAssessment assess(
            List<AlchemyCatalyst> catalysts, AlchemyMachineCondition condition) {
        Objects.requireNonNull(catalysts, "catalysts");
        Objects.requireNonNull(condition, "condition");
        return failureModel.assess(combine(catalysts), condition);
    }

    public AlchemyFailureAssessment assess(
            AlchemyPreparation preparation, AlchemyMachineCondition condition) {
        return failureModel.assess(preparation, condition);
    }

    public AlchemyMachineState start(long nowEpochMillis, UUID batchId) {
        return start(nowEpochMillis, balance.processingDurationMillis(), batchId);
    }

    public AlchemyMachineState start(long nowEpochMillis, long durationMillis, UUID batchId) {
        Math.addExact(nowEpochMillis, durationMillis);
        return new AlchemyMachineState(AlchemyMachineState.VERSION, batchId, nowEpochMillis,
                durationMillis, AlchemyMachineState.Status.PROCESSING);
    }
}
