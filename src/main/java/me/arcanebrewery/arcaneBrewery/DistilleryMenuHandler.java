package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class DistilleryMenuHandler implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey inventoryStorageKey;
    private final Map<Inventory, Block> openDistilleries = new HashMap<>();

    public DistilleryMenuHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.inventoryStorageKey = new NamespacedKey(plugin, "distillery_inventory");
    }

    @EventHandler
    public void onMachineInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        if (!DistilleryStructureScanner.isValidDistillery(clicked)) return;
        event.setCancelled(true);

        Block standBlock = DistilleryStructureScanner.getDistilleryStand(clicked);
        Player player = event.getPlayer();

        Inventory customBrewUI = Bukkit.createInventory(null, 9, "§0Arcane Distillery Core");

        // Static guidelines
        ItemStack guideGlass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        customBrewUI.setItem(3, guideGlass);
        customBrewUI.setItem(5, guideGlass);
        customBrewUI.setItem(6, guideGlass);
        customBrewUI.setItem(7, guideGlass);
        customBrewUI.setItem(8, new ItemStack(Material.REDSTONE_TORCH));

        // Catch offline delta changes prior to display
        loadInventoryFromBlock(standBlock, customBrewUI);
        DistilleryProcessTask.evaluateOfflineBoiling(plugin, customBrewUI, standBlock);

        openDistilleries.put(customBrewUI, standBlock);
        player.openInventory(customBrewUI);
    }

    @EventHandler
    public void onCustomUiClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!openDistilleries.containsKey(inv)) return;

        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            return;
        }

        int slot = event.getSlot();
        if (slot == 3 || slot == 5 || slot == 6 || slot == 7 || slot == 8) {
            event.setCancelled(true);
            return;
        }

        Block standBlock = openDistilleries.get(inv);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            DistilleryProcessTask.triggerBoilingSequence(plugin, inv, standBlock);
            saveInventoryToBlock(standBlock, inv);
        }, 1L);
    }

    @EventHandler
    public void onUiClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (openDistilleries.containsKey(inv)) {
            Block standBlock = openDistilleries.get(inv);
            saveInventoryToBlock(standBlock, inv);
            openDistilleries.remove(inv);
        }
    }

    private void saveInventoryToBlock(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(4);
            dataOutput.writeObject(inv.getItem(0));
            dataOutput.writeObject(inv.getItem(1));
            dataOutput.writeObject(inv.getItem(2));
            dataOutput.writeObject(inv.getItem(4));
            dataOutput.close();
            String serialized = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            tileState.getPersistentDataContainer().set(inventoryStorageKey, PersistentDataType.STRING, serialized);
            tileState.update();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadInventoryFromBlock(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        String serialized = tileState.getPersistentDataContainer().get(inventoryStorageKey, PersistentDataType.STRING);
        if (serialized == null || serialized.isEmpty()) return;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(serialized));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            dataInput.readInt();
            inv.setItem(0, (ItemStack) dataInput.readObject());
            inv.setItem(1, (ItemStack) dataInput.readObject());
            inv.setItem(2, (ItemStack) dataInput.readObject());
            inv.setItem(4, (ItemStack) dataInput.readObject());
            dataInput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}