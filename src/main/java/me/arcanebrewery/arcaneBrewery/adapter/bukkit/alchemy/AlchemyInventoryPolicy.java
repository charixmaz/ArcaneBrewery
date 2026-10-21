package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import org.bukkit.event.inventory.*;

/** All top mutations are manual; indirect actions are rejected even when originating in the player inventory. */
public final class AlchemyInventoryPolicy {
    public enum Decision { PASS, CANCEL, EDIT, START }
    private AlchemyInventoryPolicy() {}
    public static Decision click(boolean top, int slot, boolean processing, ClickType click, InventoryAction action) {
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return Decision.CANCEL;
        boolean ordinary = switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, PLACE_ALL, PLACE_SOME, PLACE_ONE,
                    SWAP_WITH_CURSOR, NOTHING -> true;
            default -> false;
        };
        if (!ordinary) return Decision.CANCEL;
        if (!top) return Decision.PASS;
        if (processing) return Decision.CANCEL;
        if (slot == 8) return Decision.START;
        return slot == 2 || slot == 4 || slot == 6 ? Decision.EDIT : Decision.CANCEL;
    }
    public static boolean dragTouchesMachine(java.util.Set<Integer> rawSlots, int topSize) {
        return rawSlots.stream().anyMatch(slot -> slot >= 0 && slot < topSize);
    }
}
