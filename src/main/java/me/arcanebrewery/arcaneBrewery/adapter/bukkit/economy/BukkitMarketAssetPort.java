package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class BukkitMarketAssetPort implements MarketAssetPort {
    private final CanonicalMarketBottleReader reader;
    private final MarketEscrowStore escrow;

    public BukkitMarketAssetPort(CanonicalMarketBottleReader reader, MarketEscrowStore escrow) {
        this.reader = Objects.requireNonNull(reader);
        this.escrow = Objects.requireNonNull(escrow);
    }

    @Override
    public boolean removeForSale(UUID playerId, MarketAsset asset, UUID transactionId) {
        requireServerThread();
        Objects.requireNonNull(playerId);
        Objects.requireNonNull(asset);
        Objects.requireNonNull(transactionId);

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return false;

        var inventory = player.getInventory();
        int slot = -1;
        ItemStack exact = null;
        for (int i = 0; i <= 40; i++) {
            ItemStack candidate = inventory.getItem(i);
            var read = reader.read(candidate);
            if (read.isPresent() && read.get().equals(asset)) {
                slot = i;
                exact = candidate;
                break;
            }
        }
        if (slot < 0 || exact == null) return false;

        byte[] payload = exact.serializeAsBytes();
        if (!escrow.stage(transactionId, playerId, asset.token(), slot, payload)) return false;

        inventory.setItem(slot, null);
        escrow.markRemoved(transactionId);
        return true;
    }

    @Override
    public boolean restoreAfterFailedSale(UUID playerId, MarketAsset asset, UUID transactionId) {
        requireServerThread();
        var entry = escrow.find(transactionId).orElse(null);
        if (entry == null
                || !entry.playerId().equals(playerId)
                || !entry.assetToken().equals(asset.token())) {
            return false;
        }
        if (entry.state() == MarketEscrowEntry.State.RESTORED) return true;
        if (entry.state() != MarketEscrowEntry.State.REMOVED) return false;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return false;

        ItemStack item;
        try {
            item = ItemStack.deserializeBytes(entry.itemBytes());
        } catch (RuntimeException malformed) {
            escrow.markReconcile(transactionId);
            return false;
        }
        var decoded = reader.read(item);
        if (decoded.isEmpty() || !decoded.get().equals(asset)) {
            escrow.markReconcile(transactionId);
            return false;
        }

        var inventory = player.getInventory();
        ItemStack existing = inventory.getItem(entry.inventorySlot());
        if (existing == null || existing.getType() == Material.AIR) {
            inventory.setItem(entry.inventorySlot(), item);
        } else {
            Map<Integer, ItemStack> leftovers = inventory.addItem(item);
            if (!leftovers.isEmpty()) return false;
        }

        escrow.markRestored(transactionId);
        return true;
    }

    @Override
    public void finalizeSale(UUID playerId, MarketAsset asset, UUID transactionId) {
        requireServerThread();
        var entry = escrow.find(transactionId).orElseThrow(
                () -> new IllegalStateException("market escrow missing during finalization"));
        if (!entry.playerId().equals(playerId) || !entry.assetToken().equals(asset.token())) {
            escrow.markReconcile(transactionId);
            throw new IllegalStateException("market escrow does not match finalized sale");
        }
        if (entry.state() == MarketEscrowEntry.State.RELEASED) return;
        if (entry.state() != MarketEscrowEntry.State.REMOVED) {
            escrow.markReconcile(transactionId);
            throw new IllegalStateException("market escrow is not removed during finalization");
        }
        escrow.release(transactionId);
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("market inventory operation must run on server thread");
    }
}
