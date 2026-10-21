package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;

public final class ConsumptionRecoveryTask implements Runnable {
    private final ConsumptionListener consumption;

    public ConsumptionRecoveryTask(ConsumptionListener consumption) {
        this.consumption = java.util.Objects.requireNonNull(consumption);
    }

    @Override
    public void run() {
        for (var player : Bukkit.getOnlinePlayers()) {
            consumption.refresh(player, true);
        }
    }
}
