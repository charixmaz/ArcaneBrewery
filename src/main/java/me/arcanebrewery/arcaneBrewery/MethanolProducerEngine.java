package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
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
    private final NamespacedKey slotTimeKey;
    private final NamespacedKey recipeTypeKey;
    private final NamespacedKey producerStorageKey;

    private final Map<Inventory, Block> producerSourceMap = new HashMap<>();
    private final Map<Inventory, BukkitRunnable> liveTasks = new HashMap<>();

    public MethanolProducerEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.slotTimeKey = new NamespacedKey(plugin, "slot_start_time");
        this.recipeTypeKey = new NamespacedKey(plugin, "brew_stage");
        this.producerStorageKey = new NamespacedKey(plugin, "producer_inventory_storage");
    }

    @EventHandler
    public void onProducerSignWrite(SignChangeEvent event) {
        if (event.getLine(0) != null && event.getLine(0).equalsIgnoreCase("[Producer]")) {
            event.setLine(0, "§8§l[Producer]");
            event.setLine(1, "§7Click to activate");
            event.setLine(2, "§7refinery unit");

            if (event.getBlock().getState() instanceof Sign sign) {
                sign.setWaxed(true);
                sign.getSide(Side.FRONT).setLine(0, "§8§l[Producer]");
                sign.update();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProducerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !(clickedBlock.getState() instanceof Sign sign)) return;

        SignSide front = sign.getSide(Side.FRONT);
        if (!front.getLine(0).contains("[Producer]")) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Block coreBlock = findAdjacentCoreBlock(clickedBlock);

        if (coreBlock == null) {
            player.sendMessage("§c[Brewery] Refinery sign must be attached directly to a solid block core!");
            return;
        }

        if (!verifyIndustrialStructure(coreBlock)) {
            player.sendMessage("§c[Brewery] Invalid factory structure! Check your Polished Deepslate Stair arrangement and Gray Concrete core blocks.");
            front.setLine(1, "§cSTRUCTURE");
            front.setLine(2, "§cMISMATCH");
            sign.update();
            return;
        }

        front.setLine(1, "§eMethanol Unit");
        front.setLine(2, "§a✔ OPERATIONAL");
        sign.update();

        Inventory producerGui = Bukkit.createInventory(null, 9, "§8Industrial Methanol Unit");
        loadInventoryFromCore(coreBlock, producerGui);

        producerSourceMap.put(producerGui, coreBlock);
        startProducerTicker(producerGui);
        player.openInventory(producerGui);
    }

    @EventHandler
    public void onProducerGuiClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!producerSourceMap.containsKey(inv)) return;

        Block coreBlock = producerSourceMap.get(inv);
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Lock down industrial decorative custom glass slots
        if (event.getClickedInventory() == inv) {
            if (slot == 1 || slot == 3 || slot == 5 || slot == 7 || slot == 8) {
                event.setCancelled(true);
                return;
            }

            // Force shift-click style extraction mechanic for processing lines
            if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) {
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() == Material.POTION) {
                    event.setCancelled(true);
                    player.sendMessage("§e[Brewery] Use SHIFT-CLICK to extract compounds from the pipeline!");
                    return;
                }
            }
        }

        // Handle Shift-clicking items from bottom inventory INTO processing lanes
        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && currentItem.getType() == Material.POTION) {
                    PotionMeta meta = (PotionMeta) currentItem.getItemMeta();
                    if (meta != null) {
                        String stage = meta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "");

                        // STRICT ENTRY PERMIT: The producer layout ONLY accepts Refined Industrial Alcohol base compounds!
                        if (!stage.equalsIgnoreCase("REFINED_INDUSTRIAL_ALCOHOL")) {
                            event.setCancelled(true);
                            player.sendMessage("§c[Producer] Lane Rejected! This factory only accepts boiled REFINED INDUSTRIAL ALCOHOL.");
                            return;
                        }

                        // Snap item seamlessly into process channels
                        event.setCancelled(true);
                        int[] targetSlots = {0, 2, 4, 6};
                        for (int targetSlot : targetSlots) {
                            ItemStack existing = inv.getItem(targetSlot);
                            if (existing == null || existing.getType() == Material.AIR) {
                                ItemStack processedItem = currentItem.clone();
                                processedItem.setAmount(1);

                                PotionMeta pMeta = (PotionMeta) processedItem.getItemMeta();
                                pMeta.getPersistentDataContainer().set(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                                processedItem.setItemMeta(pMeta);

                                inv.setItem(targetSlot, processedItem);

                                if (currentItem.getAmount() > 1) {
                                    currentItem.setAmount(currentItem.getAmount() - 1);
                                } else {
                                    event.getClickedInventory().setItem(event.getSlot(), null);
                                }
                                saveInventoryToCore(coreBlock, inv);
                                return;
                            }
                        }
                    }
                }
            }
            return;
        }

        // Process actual product extraction runs (Shift click outputs)
        if (event.getClickedInventory() == inv && (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) {
            ItemStack potion = event.getCurrentItem();
            if (potion != null && potion.getType() == Material.POTION) {
                PotionMeta meta = (PotionMeta) potion.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(slotTimeKey, PersistentDataType.LONG)) {
                    event.setCancelled(true);

                    long startTime = meta.getPersistentDataContainer().getOrDefault(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;

                    // Industrial refinery rules: requires exactly 30 seconds processing loop to drop pure Methanol
                    if (elapsed < 30) {
                        player.sendMessage("§c[Producer] Processing incomplete! Fluid condensation lines are still running.");
                        return;
                    }

                    // Generate complete customized Industrial Methanol Fluid Item
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
                    player.sendMessage("§a[Producer] ✔ Successfully collected 1x bottle of pure Industrial Methanol Fluid.");
                }
            }
        }
    }

    @EventHandler
    public void onProducerClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (producerSourceMap.containsKey(inv)) {
            Block coreBlock = producerSourceMap.get(inv);
            if (liveTasks.containsKey(inv)) {
                liveTasks.get(inv).cancel();
                liveTasks.remove(inv);
            }
            saveInventoryToCore(coreBlock, inv);
            producerSourceMap.remove(inv);
        }
    }

    private void startProducerTicker(Inventory inv) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Block coreBlock = producerSourceMap.get(inv);
                if (coreBlock == null) return;
                ProducerVisualEngine.updateProducerInterface(plugin, inv, coreBlock);
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        liveTasks.put(inv, task);
    }

    private boolean verifyIndustrialStructure(Block core) {
        int deepslateStairsCount = 0;
        int grayConcreteCount = 0;
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Material mat = core.getRelative(x, y, z).getType();
                    if (mat.name().contains("DEEPSLATE_STAIRS")) deepslateStairsCount++;
                    if (mat == Material.GRAY_CONCRETE) grayConcreteCount++;
                }
            }
        }
        return deepslateStairsCount >= 8 && grayConcreteCount >= 4;
    }

    private Block findAdjacentCoreBlock(Block signBlock) {
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = signBlock.getRelative(face);
            if (neighbor.getType() == Material.GRAY_CONCRETE || neighbor.getType() == Material.BARREL) return neighbor;
        }
        return null;
    }

    private void saveInventoryToCore(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(9);
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType().name().endsWith("_GLASS_PANE")) item = null;
                dataOutput.writeObject(item);
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
                inv.setItem(i, (ItemStack) dataInput.readObject());
            }
            dataInput.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}