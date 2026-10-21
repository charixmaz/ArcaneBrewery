package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;
import java.util.function.Function;

/** Fixed-slot native NBT boundary; injectable conversion keeps framing tests independent of a server. */
public final class AlchemyMachineItems {
    private final Function<ItemStack, byte[]> encode;
    private final Function<byte[], ItemStack> decode;
    public AlchemyMachineItems() { this(ItemStack::serializeAsBytes, ItemStack::deserializeBytes); }
    public AlchemyMachineItems(Function<ItemStack, byte[]> encode, Function<byte[], ItemStack> decode) {
        this.encode = Objects.requireNonNull(encode); this.decode = Objects.requireNonNull(decode);
    }
    public List<byte[]> encode(ItemStack[] items) {
        if (items.length != 3) throw new IllegalArgumentException("expected three input slots");
        return Arrays.stream(items).map(item -> empty(item) ? new byte[0] : encode.apply(valid(item).clone())).toList();
    }
    public ItemStack[] decode(List<byte[]> payloads) {
        if (payloads.size() != 3) throw new IllegalArgumentException("expected three item payloads");
        return payloads.stream().map(bytes -> bytes.length == 0 ? null : valid(decode.apply(bytes.clone()))).toArray(ItemStack[]::new);
    }
    private ItemStack valid(ItemStack item) {
        if (empty(item) || item.getAmount() > item.getMaxStackSize())
            throw new IllegalArgumentException("invalid saved machine stack");
        return item;
    }
    public static boolean empty(ItemStack item) { return item == null || item.getAmount() <= 0 || item.getType() == Material.AIR; }
}
