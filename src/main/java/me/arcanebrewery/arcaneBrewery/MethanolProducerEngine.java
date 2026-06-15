package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class MethanolProducerEngine implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey recipeTypeKey;
    private final NamespacedKey slotTimeKey;
    private final NamespacedKey producerStorageKey;

    private final Map<Inventory, Block> producerSourceMap = new HashMap<>();
    private final Map<Inventory, BukkitRunnable> liveVisualTasks = new HashMap<>();

    public MethanolProducerEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.recipeTypeKey = new NamespacedKey(plugin, "brew_stage");
        this.slotTimeKey = new NamespacedKey(plugin, "slot_start_time");
        this.producerStorageKey = new NamespacedKey(plugin, "producer_inventory_storage");
    }

    // NEW LOCAL HELPER: Fixes the missing method error in the image
    private Block findAdjacentCore(Block signBlock) {
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = signBlock.getRelative(face);
            if (neighbor.getType() == Material.GRAY_CONCRETE) {
                return neighbor;
            }
        }
        return null;
    }

    @EventHandler
    public void onProducerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !(clickedBlock.getState() instanceof Sign sign)) return;

        if (!sign.getLine(0).contains("[Producer]")) return;
        event.setCancelled(true);

        Player player = event.getPlayer();

        // FIX: Replaced the broken method call with our safe local block scanner
        Block coreBlock = findAdjacentCore(clickedBlock);

        if (coreBlock == null) {
            player.sendMessage(MessageConfig.get("structure_broken"));
            sign.setLine(1, "§cINVALID");
            sign.setLine(2, "§cSTRUCTURE");
            sign.update();
            return;
        }

        sign.setLine(1, "§8Core Pressurized");
        sign.setLine(2, "§2✔ ONLINE");
        sign.update();

        Inventory producerGui = Bukkit.createInventory(null, 9, "§0Methanol Producer Core");

        loadInventoryFromCore(coreBlock, producerGui);
        producerSourceMap.put(producerGui, coreBlock);

        startLivePaneUpdater(producerGui);
        player.openInventory(producerGui);
    }

    @EventHandler
    public void onProducerGuiClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!producerSourceMap.containsKey(inv)) return;

        Block coreBlock = producerSourceMap.get(inv);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        if (event.isCancelled()) return;

        if (event.getClickedInventory() == inv) {
            if (slot == 1 || slot == 3 || slot == 5 || slot == 7 || slot == 8) {
                event.setCancelled(true);
                return;
            }

            if (event.getCursor() != null && event.getCursor().getType() != Material.AIR) {
                ItemStack cursor = event.getCursor();
                if (cursor.getType() != Material.POTION) {
                    event.setCancelled(true);
                    player.sendMessage(MessageConfig.get("producer_reject_potion"));
                    return;
                }

                PotionMeta meta = (PotionMeta) cursor.getItemMeta();
                if (meta != null) {
                    String stage = meta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "");
                    if (!stage.equalsIgnoreCase("REFINED_INDUSTRIAL_ALCOHOL")) {
                        event.setCancelled(true);
                        player.sendMessage(MessageConfig.get("producer_reject"));
                        return;
                    }
                }
            }
        }

        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && currentItem.getType() == Material.POTION) {
                    PotionMeta meta = (PotionMeta) currentItem.getItemMeta();
                    if (meta != null) {
                        String stage = meta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "");

                        if (!stage.equalsIgnoreCase("REFINED_INDUSTRIAL_ALCOHOL")) {
                            event.setCancelled(true);
                            player.sendMessage(MessageConfig.get("producer_reject"));
                            return;
                        }

                        event.setCancelled(true);
                        int[] targetSlots = {0, 2, 4, 6};
                        for (int targetSlot : targetSlots) {
                            ItemStack existing = inv.getItem(targetSlot);
                            if (existing == null || existing.getType() == Material.AIR || existing.getType().name().endsWith("_GLASS_PANE")) {
                                ItemStack processedItem = currentItem.clone();
                                processedItem.setAmount(1);

                                PotionMeta pMeta = (PotionMeta) processedItem.getItemMeta();
                                pMeta.getPersistentDataContainer().set(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                                processedItem.setItemMeta(pMeta);

                                inv.setItem(targetSlot, processedItem);

                                if (currentItem.getAmount() > 1) {
                                    currentItem.setAmount(currentItem.getAmount() - 1);
                                    event.getClickedInventory().setItem(event.getSlot(), currentItem);
                                } else {
                                    event.getClickedInventory().setItem(event.getSlot(), null);
                                }

                                saveInventoryToCore(coreBlock, inv);
                                player.updateInventory();
                                return;
                            }
                        }
                    }
                }
            }
            return;
        }

        if (event.getClickedInventory() == inv && (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            ItemStack potion = event.getCurrentItem();
            if (potion != null && potion.getType() == Material.POTION) {
                PotionMeta meta = (PotionMeta) potion.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(slotTimeKey, PersistentDataType.LONG)) {
                    event.setCancelled(true);

                    long startTime = meta.getPersistentDataContainer().getOrDefault(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;

                    if (elapsed < 10) {
                        player.sendMessage(MessageConfig.get("producer_incomplete"));
                        return;
                    }

                    ItemStack methanolBottle = new ItemStack(Material.POTION);
                    PotionMeta mMeta = (PotionMeta) methanolBottle.getItemMeta();
                    if (mMeta != null) {
                        mMeta.setDisplayName("§b§lIndustrial Methanol Fluid");
                        mMeta.setLore(java.util.List.of(
                                "§7Type: §fRefined Heavy Chemical",
                                "§7Purity Level: §b99.8% Concentrate",
                                "",
                                "§4§l⚠ TOXIC FLUID: DO NOT DRINK",
                                "§d§oDrop into Cask input valves to sterilize mold."
                        ));
                        mMeta.getPersistentDataContainer().set(recipeTypeKey, PersistentDataType.STRING, "INDUSTRIAL_METHANOL");
                        methanolBottle.setItemMeta(mMeta);
                    }

                    inv.setItem(slot, null);
                    int paneSlot = slot + 1;
                    if (paneSlot < 9) inv.setItem(paneSlot, new ItemStack(Material.AIR));

                    saveInventoryToCore(coreBlock, inv);
                    player.getInventory().addItem(methanolBottle);
                    player.sendMessage(MessageConfig.get("producer_success"));
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemStack itemInSlot = inv.getItem(slot);
            if (itemInSlot != null && itemInSlot.getType() == Material.POTION) {
                PotionMeta meta = (PotionMeta) itemInSlot.getItemMeta();
                if (meta != null && !meta.getPersistentDataContainer().has(slotTimeKey, PersistentDataType.LONG)) {
                    meta.getPersistentDataContainer().set(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                    itemInSlot.setItemMeta(meta);
                }
            }
            saveInventoryToCore(coreBlock, inv);
        }, 1L);
    }

    @EventHandler
    public void onProducerClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (producerSourceMap.containsKey(inv)) {
            Block coreBlock = producerSourceMap.get(inv);

            if (liveVisualTasks.containsKey(inv)) {
                liveVisualTasks.get(inv).cancel();
                liveVisualTasks.remove(inv);
            }

            saveInventoryToCore(coreBlock, inv);
            producerSourceMap.remove(inv);
        }
    }

    private void startLivePaneUpdater(Inventory inv) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Block coreBlock = producerSourceMap.get(inv);
                if (coreBlock == null) return;
                ProducerVisualEngine.updateProducerInterface(plugin, inv, coreBlock);
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        liveVisualTasks.put(inv, task);
    }

    private void saveInventoryToCore(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(9);
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() == Material.POTION) {
                    dataOutput.writeObject(item);
                } else {
                    dataOutput.writeObject(null);
                }
            }
            dataOutput.close();
            String serialized = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            tileState.getPersistentDataContainer().set(producerStorageKey, PersistentDataType.STRING, serialized);
            tileState.update();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadInventoryFromCore(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        String serialized = tileState.getPersistentDataContainer().get(producerStorageKey, PersistentDataType.STRING);
        if (serialized == null || serialized.isEmpty()) return;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(serialized));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            for (int i = 0; i < size; i++) {
                ItemStack loaded = (ItemStack) dataInput.readObject();
                if (loaded != null && loaded.getType() == Material.POTION) {
                    inv.setItem(i, loaded);
                }
            }
            dataInput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}