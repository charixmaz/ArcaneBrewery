package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import org.bukkit.inventory.ItemStack;

/** Normal left/right interactions computed on clones before persistence or cursor mutation. */
public final class AlchemySlotTransfer {
    public record Move(ItemStack slot, ItemStack cursor) {}
    private AlchemySlotTransfer() {}
    public static Move move(ItemStack slot, ItemStack cursor, boolean right, int slotLimit) {
        slot = copy(slot); cursor = copy(cursor);
        if (cursor == null) {
            if (slot == null) return new Move(null, null);
            int take = right ? (slot.getAmount() + 1) / 2 : slot.getAmount();
            ItemStack picked = slot.clone(); picked.setAmount(take);
            return new Move(amount(slot, slot.getAmount() - take), picked);
        }
        int limit = Math.min(slotLimit, cursor.getMaxStackSize());
        if (slot == null || slot.isSimilar(cursor)) {
            int existing = slot == null ? 0 : slot.getAmount();
            int transfer = Math.min(right ? 1 : cursor.getAmount(), Math.max(0, limit - existing));
            if (transfer == 0) return new Move(slot, cursor);
            ItemStack placed = cursor.clone(); placed.setAmount(existing + transfer);
            return new Move(placed, amount(cursor, cursor.getAmount() - transfer));
        }
        return cursor.getAmount() <= limit ? new Move(cursor, slot) : new Move(slot, cursor);
    }
    private static ItemStack copy(ItemStack item) { return AlchemyMachineItems.empty(item) ? null : item.clone(); }
    private static ItemStack amount(ItemStack item, int amount) {
        if (amount == 0) return null;
        item.setAmount(amount); return item;
    }
}
