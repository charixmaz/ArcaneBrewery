package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.*;

/** Implementations hand off only item access to the server thread. No database work here. */
public interface MarketInventoryPort {
    record Item(int slot, byte[] bytes) {
        public Item { bytes=bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    Optional<Item> snapshot(UUID player, MarketAsset asset, UUID transaction);
    boolean removeExact(UUID player, Item item);
    boolean restore(UUID player, MarketAsset asset, Item item);
}
