package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Objects;

public final class AlchemyMaintenanceService {
    private final AlchemyBalanceProfile balance;

    public AlchemyMaintenanceService(AlchemyBalanceProfile balance) {
        this.balance = Objects.requireNonNull(balance);
    }

    public AlchemyMachineCondition repair(AlchemyMachineCondition condition) {
        Objects.requireNonNull(condition);
        return condition.service(balance.repairIntegrity(), 0);
    }

    public AlchemyMachineCondition clean(AlchemyMachineCondition condition) {
        Objects.requireNonNull(condition);
        return condition.service(0, balance.cleaningAmount());
    }
}
