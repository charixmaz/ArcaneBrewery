package me.arcanebrewery.arcaneBrewery.application.alchemy;

public record AlchemyMachineCondition(double integrity, double contamination) {
    public static final AlchemyMachineCondition PRISTINE = new AlchemyMachineCondition(100.0, 0.0);

    public AlchemyMachineCondition {
        integrity = percent(integrity, "integrity");
        contamination = percent(contamination, "contamination");
    }

    public AlchemyMachineCondition wear(double integrityLoss, double contaminationGain) {
        if (!Double.isFinite(integrityLoss) || integrityLoss < 0
                || !Double.isFinite(contaminationGain) || contaminationGain < 0)
            throw new IllegalArgumentException("machine wear must be non-negative");
        return new AlchemyMachineCondition(
                Math.max(0, integrity - integrityLoss),
                Math.min(100, contamination + contaminationGain)
        );
    }

    public AlchemyMachineCondition service(double integrityRestore, double contaminationRemoved) {
        if (!Double.isFinite(integrityRestore) || integrityRestore < 0
                || !Double.isFinite(contaminationRemoved) || contaminationRemoved < 0)
            throw new IllegalArgumentException("machine service values must be non-negative");
        return new AlchemyMachineCondition(
                Math.min(100, integrity + integrityRestore),
                Math.max(0, contamination - contaminationRemoved)
        );
    }

    private static double percent(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 100)
            throw new IllegalArgumentException(name + " must be within 0..100");
        return value;
    }
}
