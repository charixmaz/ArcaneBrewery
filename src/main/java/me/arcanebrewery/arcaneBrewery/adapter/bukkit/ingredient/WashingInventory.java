package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ShiftTransfers;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;

/** Plan a whole one-item wash before consuming the ingredient or cauldron water. */
public final class WashingInventory {
    private WashingInventory() { }

    public static Optional<ItemStack[]> plan(ItemStack[] storage, int heldSlot, ItemStack washed) {
        if (heldSlot < 0 || heldSlot >= storage.length || washed == null || washed.getAmount() != 1)
            throw new IllegalArgumentException("Invalid washing delivery");
        var next = Arrays.stream(storage).map(item -> item == null || item.getType() == Material.AIR
                || item.getType() == Material.CAVE_AIR || item.getType() == Material.VOID_AIR
                || item.getAmount() <= 0 ? null : item.clone()).toArray(ItemStack[]::new);
        var held = next[heldSlot];
        if (held == null || held.getType() != washed.getType())
            throw new IllegalArgumentException("Missing washing ingredient");
        if (held.getAmount() == 1) {
            // The consumed item frees its own slot, even when every other slot is full.
            next[heldSlot] = washed.clone();
            return Optional.of(next);
        }
        held.setAmount(held.getAmount() - 1);
        var delivery = ShiftTransfers.plan(washed, next, IntStream.range(0, next.length).toArray(),
                slot -> 64, (slot, item) -> true);
        return delivery.remainder() == null ? Optional.of(delivery.destination()) : Optional.empty();
    }
}
