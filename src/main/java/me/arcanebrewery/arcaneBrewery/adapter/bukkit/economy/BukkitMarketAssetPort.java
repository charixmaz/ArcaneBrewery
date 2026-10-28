package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;

import me.arcanebrewery.arcaneBrewery.application.economy.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.ShiftTransfers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Escrow stays on the market worker; only snapshots and mutations enter Bukkit's thread. */
public final class BukkitMarketAssetPort implements MarketAssetPort {
    private record Selection(UUID player, int slot, ItemStack item) {}
    private final CanonicalMarketBottleReader reader;
    private final MarketSettlementRuntime runtime;
    private final EscrowMarketAssetPort delegate;
    // Accessed only on the server thread.
    private final Map<UUID,Selection> selected=new HashMap<>();

    public BukkitMarketAssetPort(CanonicalMarketBottleReader reader, MarketEscrowStore escrow, MarketSettlementRuntime runtime) {
        this.reader=Objects.requireNonNull(reader); this.runtime=Objects.requireNonNull(runtime);
        delegate=new EscrowMarketAssetPort(new MarketInventoryPort() {
            public Optional<Item> snapshot(UUID player, MarketAsset asset, UUID transaction) {
                return runtime.inventory(()->capture(player,asset,transaction));
            }
            public boolean removeExact(UUID player, Item item) {
                return runtime.inventory(()->remove(player,item));
            }
            public boolean restore(UUID player, MarketAsset asset, Item item) {
                return runtime.inventory(()->restoreItem(player,asset,item));
            }
        },escrow);
    }
    public void select(UUID player, UUID transaction, int slot, ItemStack item) {
        requireServerThread();
        if(slot<0 || slot>8 || item==null || item.getAmount()!=1) throw new IllegalArgumentException("invalid held sale selection");
        if(selected.size()>=256) throw new IllegalStateException("too many queued sales");
        if(selected.containsKey(transaction)) throw new IllegalArgumentException("transaction already selected");
        selected.put(transaction,new Selection(player,slot,item.clone()));
    }
    public void clearSelection(UUID transaction) { requireServerThread(); selected.remove(transaction); }

    private Optional<MarketInventoryPort.Item> capture(UUID playerId, MarketAsset asset, UUID transaction) {
        requireServerThread();
        var selection=selected.remove(transaction);
        Player player=Bukkit.getPlayer(playerId);
        if(selection==null || !selection.player().equals(playerId) || player==null || !player.isOnline()) return Optional.empty();
        var inventory=player.getInventory();
        var current=inventory.getItem(selection.slot());
        if(inventory.getHeldItemSlot()!=selection.slot() || !same(current,selection.item())
                || reader.read(current).filter(asset::equals).isEmpty()) return Optional.empty();
        return Optional.of(new MarketInventoryPort.Item(selection.slot(),current.serializeAsBytes()));
    }
    private boolean remove(UUID playerId, MarketInventoryPort.Item snapshot) {
        requireServerThread();
        var player=Bukkit.getPlayer(playerId);
        if(player==null || !player.isOnline()) return false;
        var inventory=player.getInventory();
        var exact=ItemStack.deserializeBytes(snapshot.bytes());
        if(inventory.getHeldItemSlot()!=snapshot.slot() || !same(inventory.getItem(snapshot.slot()),exact)) return false;
        inventory.setItem(snapshot.slot(),null);
        return true;
    }
    private boolean restoreItem(UUID playerId, MarketAsset asset, MarketInventoryPort.Item snapshot) {
        requireServerThread();
        var player=Bukkit.getPlayer(playerId);
        if(player==null || !player.isOnline()) return false;
        ItemStack item;
        try { item=ItemStack.deserializeBytes(snapshot.bytes()); }
        catch(RuntimeException malformed) { return false; }
        if(reader.read(item).filter(asset::equals).isEmpty()) return false;
        var inventory=player.getInventory();
        var current=inventory.getItem(snapshot.slot());
        if(current==null || current.getType().isAir()) inventory.setItem(snapshot.slot(),item);
        else {
            var contents=inventory.getStorageContents();
            var plan=ShiftTransfers.plan(item,contents,java.util.stream.IntStream.range(0,contents.length).toArray(),
                    i->64,(i,value)->true);
            if(plan.remainder()!=null) return false;
            inventory.setStorageContents(plan.destination());
        }
        return true;
    }
    public static boolean same(ItemStack current, ItemStack quoted) {
        return current!=null && quoted!=null && current.getAmount()==quoted.getAmount() && current.isSimilar(quoted);
    }
    public boolean removeForSale(UUID p, MarketAsset a, UUID id) { requireWorkerThread(); return delegate.removeForSale(p,a,id); }
    public boolean restoreAfterFailedSale(UUID p, MarketAsset a, UUID id) { requireWorkerThread(); return delegate.restoreAfterFailedSale(p,a,id); }
    public void finalizeSale(UUID p, MarketAsset a, UUID id) { requireWorkerThread(); delegate.finalizeSale(p,a,id); }
    private static void requireWorkerThread() {
        if(Bukkit.isPrimaryThread()) throw new IllegalStateException("market escrow must run on worker thread");
    }
    private static void requireServerThread() {
        if(!Bukkit.isPrimaryThread()) throw new IllegalStateException("market inventory operation must run on server thread");
    }
}
