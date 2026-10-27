package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.MachineAutomationHolder;
import org.bukkit.event.inventory.*;
/** Acquisition events are hints to inspect resulting possession, never a source of an item identity. */
public final class MaterialAcquisitionPolicy {
    private MaterialAcquisitionPolicy() { }
    public static boolean click(InventoryClickEvent event) {
        return !event.isCancelled() && !(event.getView().getTopInventory().getHolder() instanceof MachineAutomationHolder)
            && event.getAction()!=InventoryAction.NOTHING && event.getAction()!=InventoryAction.UNKNOWN;
    }
    public static boolean drag(InventoryDragEvent event) {
        return !event.isCancelled() && !(event.getView().getTopInventory().getHolder() instanceof MachineAutomationHolder);
    }
}
