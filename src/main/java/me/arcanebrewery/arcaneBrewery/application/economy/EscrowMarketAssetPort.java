package me.arcanebrewery.arcaneBrewery.application.economy;

import java.util.*;

/** Worker-thread coordinator. Each inventory handoff revalidates its exact snapshot. */
public final class EscrowMarketAssetPort implements MarketAssetPort {
    private final MarketInventoryPort inventory;
    private final MarketEscrowStore escrow;
    public EscrowMarketAssetPort(MarketInventoryPort inventory, MarketEscrowStore escrow) {
        this.inventory=Objects.requireNonNull(inventory); this.escrow=Objects.requireNonNull(escrow);
    }
    public boolean removeForSale(UUID player, MarketAsset asset, UUID transaction) {
        var item=inventory.snapshot(player,asset,transaction).orElse(null);
        if(item==null || !escrow.stage(transaction,player,asset.token(),item.slot(),item.bytes())) return false;
        if(!inventory.removeExact(player,item)) {
            escrow.markRestored(transaction); // Nothing was removed; retire the staged reservation.
            return false;
        }
        escrow.markRemoved(transaction);
        return true;
    }
    public boolean restoreAfterFailedSale(UUID player, MarketAsset asset, UUID transaction) {
        var entry=escrow.find(transaction).orElse(null);
        if(entry==null || !entry.playerId().equals(player) || !entry.assetToken().equals(asset.token())) return false;
        if(entry.state()==MarketEscrowEntry.State.RESTORED) return true;
        if(entry.state()!=MarketEscrowEntry.State.REMOVED) return false;
        if(!inventory.restore(player,asset,new MarketInventoryPort.Item(entry.inventorySlot(),entry.itemBytes()))) return false;
        escrow.markRestored(transaction);
        return true;
    }
    public void finalizeSale(UUID player, MarketAsset asset, UUID transaction) {
        var entry=escrow.find(transaction).orElseThrow(()->new IllegalStateException("market escrow missing during finalization"));
        if(!entry.playerId().equals(player) || !entry.assetToken().equals(asset.token())) {
            escrow.markReconcile(transaction); throw new IllegalStateException("market escrow does not match finalized sale");
        }
        if(entry.state()==MarketEscrowEntry.State.RELEASED) return;
        if(entry.state()!=MarketEscrowEntry.State.REMOVED) {
            escrow.markReconcile(transaction); throw new IllegalStateException("market escrow is not removed during finalization");
        }
        escrow.release(transaction);
    }
}
