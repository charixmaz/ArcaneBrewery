package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.List;
import java.util.Map;

public class AgingCaskEngine implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey slotTimeKey;
    private final NamespacedKey recipeTypeKey;
    private final NamespacedKey barrelStorageKey;

    private final Map<Inventory, Block> inventorySourceMap = new HashMap<>();
    private final Map<Inventory, BukkitRunnable> liveVisualTasks = new HashMap<>();

    public AgingCaskEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.slotTimeKey = new NamespacedKey(plugin, "slot_start_time");
        this.recipeTypeKey = new NamespacedKey(plugin, "brew_stage");
        this.barrelStorageKey = new NamespacedKey(plugin, "barrel_inventory_storage");
    }

    @EventHandler
    public void onSignWrite(SignChangeEvent event) {
        if (event.getLine(0) != null) {
            String line0 = event.getLine(0);

            if (line0.equalsIgnoreCase("[Brewery]")) {
                event.setLine(0, "§a[Brewery]");
                event.setLine(1, "§7Click to lock");
                event.setLine(2, "§7casing structure");

                if (event.getBlock().getState() instanceof Sign sign) {
                    sign.setWaxed(true);
                    sign.getSide(Side.FRONT).setLine(0, "§a[Brewery]");
                    sign.update();
                }
            }
            else if (line0.equalsIgnoreCase("[Status]") || line0.equalsIgnoreCase("[Stats]")) {
                event.setLine(0, "§2§l[Diagnostics]");
                event.setLine(1, "§7Connecting to");
                event.setLine(2, "§7barrel core...");
                event.setLine(3, "§7✔ INTERLINKED");

                if (event.getBlock().getState() instanceof Sign sign) {
                    sign.setWaxed(true);
                    SignSide front = sign.getSide(Side.FRONT);
                    front.setLine(0, "§2§l[Diagnostics]");
                    front.setLine(1, "§7Connecting to");
                    front.setLine(2, "§7barrel core...");
                    front.setLine(3, "§7✔ INTERLINKED");
                    sign.update();
                }

                Block adjacentBarrel = findAdjacentBarrel(event.getBlock());
                if (adjacentBarrel != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        CaskHygieneEngine.updateExternalSigns(plugin, adjacentBarrel);
                    }, 5L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSignEditIntercept(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || !(clicked.getState() instanceof Sign sign)) return;

        String line0 = sign.getSide(Side.FRONT).getLine(0);
        if (line0.contains("[Brewery]") || line0.contains("Diagnostics") || line0.contains("[Cask") || line0.contains("☣")) {
            if (!sign.isWaxed()) {
                sign.setWaxed(true);
                sign.update();
            }

            if (line0.contains("Diagnostics") || line0.contains("[Cask") || line0.contains("☣")) {
                event.setCancelled(true);
                Block barrel = findAdjacentBarrel(clicked);
                if (barrel != null) {
                    CaskHygieneEngine.updateExternalSigns(plugin, barrel);
                }
            }
        }
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || !(clickedBlock.getState() instanceof Sign sign)) return;

        if (!sign.getSide(Side.FRONT).getLine(0).contains("[Brewery]")) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        Block barrelBlock = findAdjacentBarrel(clickedBlock);

        if (barrelBlock == null) {
            player.sendMessage("§c[Brewery] Sign must be attached directly to a Barrel block!");
            return;
        }

        String recognizedWood = scanSoftCasingEnvelope(barrelBlock);
        if (recognizedWood.equals("UNKNOWN")) {
            player.sendMessage("§c[Brewery] Cask structure invalid! Build using your cross stair/slab assembly with a plank block core.");
            sign.getSide(Side.FRONT).setLine(1, "§cINVALID");
            sign.getSide(Side.FRONT).setLine(2, "§cSTRUCTURE");
            sign.update();
            return;
        }

        sign.getSide(Side.FRONT).setLine(1, "§d" + recognizedWood + " Casing");
        sign.getSide(Side.FRONT).setLine(2, "§2✔ LOCKED & OK");
        sign.update();

        Inventory breweryGui = Bukkit.createInventory(null, 9, "§0Aging Cask Menu (" + recognizedWood + ")");
        loadInventoryFromBarrel(barrelBlock, breweryGui);

        inventorySourceMap.put(breweryGui, barrelBlock);
        startLivePaneUpdater(breweryGui);
        player.openInventory(breweryGui);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!inventorySourceMap.containsKey(inv)) return;

        Block barrelBlock = inventorySourceMap.get(inv);
        Player player = (Player) event.getWhoClicked();
        String physicalWood = scanSoftCasingEnvelope(barrelBlock);

        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        boolean isInfected = CaskHygieneEngine.checkInfectionStatus(plugin, barrelBlock);

        // --- INTERACTIVE METHANOL SANITIZATION VALVE (SLOT 8) ---
        if (slot == 8) {
            event.setCancelled(true);

            if (cursorItem != null && cursorItem.getType() == Material.POTION && cursorItem.hasItemMeta()) {
                PotionMeta meta = (PotionMeta) cursorItem.getItemMeta();
                String brewStage = meta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "");

                if (brewStage.equalsIgnoreCase("INDUSTRIAL_METHANOL")) {
                    if (!isInfected) {
                        player.sendMessage("§a[Brewery] Cask ventilation system is already fully sterile!");
                        return;
                    }

                    if (cursorItem.getAmount() > 1) {
                        cursorItem.setAmount(cursorItem.getAmount() - 1);
                    } else {
                        player.setItemOnCursor(new ItemStack(Material.GLASS_BOTTLE));
                    }

                    // FIXED Bug #1: Replaced invalid setView method invocation with rock-solid update client packet flush
                    player.updateInventory();

                    NamespacedKey contaminationKey = new NamespacedKey(plugin, "cask_contaminated");
                    NamespacedKey spoilTimeKey = new NamespacedKey(plugin, "cask_spoil_timestamp");

                    if (barrelBlock.getState() instanceof TileState tileState) {
                        tileState.getPersistentDataContainer().set(contaminationKey, PersistentDataType.INTEGER, 0);
                        tileState.getPersistentDataContainer().remove(spoilTimeKey);
                        tileState.update();
                    }

                    Block topBlock = barrelBlock.getRelative(0, 1, 0);
                    if (topBlock.getType() == Material.BROWN_MUSHROOM) {
                        topBlock.setType(Material.AIR);
                    }

                    barrelBlock.getWorld().playSound(barrelBlock.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 1.2f);
                    barrelBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, barrelBlock.getLocation().add(0.5, 1.1, 0.5), 20, 0.2, 0.2, 0.2, 0.02);

                    player.sendMessage("§a§l[Brewery] ✔ Methanol input injection successful! System flushed.");

                    CaskVisualEngine.updateCaskInterface(plugin, inv, barrelBlock);
                    CaskHygieneEngine.updateExternalSigns(plugin, barrelBlock);
                    return;
                }
            }
            return;
        }

        if (isInfected) {
            if (event.getClickedInventory() == inv) {
                event.setCancelled(true);
                player.sendMessage("§c[Brewery] ☣ Core contaminated! Drop pure METHANOL into the Slot 8 condenser valve to purge.");
                return;
            }
            if (event.getClickedInventory() == event.getView().getBottomInventory() && event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem != null && currentItem.getType() == Material.POTION) {
                    event.setCancelled(true);

                    int[] activeSlots = {0, 2, 4, 6};
                    for (int activeSlot : activeSlots) {
                        ItemStack target = inv.getItem(activeSlot);
                        if (target == null || target.getType() == Material.AIR) {
                            ItemStack insertedItem = currentItem.clone();
                            insertedItem.setAmount(1);

                            PotionMeta m = (PotionMeta) insertedItem.getItemMeta();
                            if (m != null && m.getPersistentDataContainer().has(recipeTypeKey, PersistentDataType.STRING)) {
                                m.getPersistentDataContainer().set(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                                insertedItem.setItemMeta(m);
                            }

                            inv.setItem(activeSlot, insertedItem);

                            if (currentItem.getAmount() > 1) {
                                currentItem.setAmount(currentItem.getAmount() - 1);
                            } else {
                                event.getClickedInventory().setItem(event.getSlot(), null);
                            }

                            saveInventoryToBarrel(barrelBlock, inv);
                            return;
                        }
                    }
                }
            }
            return;
        }

        // FIXED Bug #2: Deleted the duplicated assignment block statements for 'slot' and 'clickedItem' from this location!
        if (slot == 1 || slot == 3 || slot == 5 || slot == 7) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() != ClickType.SHIFT_LEFT && event.getClick() != ClickType.SHIFT_RIGHT) {
            if (event.getClickedInventory() == inv && clickedItem != null && clickedItem.getType() == Material.POTION) {
                event.setCancelled(true);
                player.sendMessage("§e[Brewery] You must Use SHIFT-CLICK to bottle and harvest finished drinks!");
                return;
            }
        }

        if (event.isShiftClick() && clickedItem != null && clickedItem.getType() == Material.POTION) {
            PotionMeta meta = (PotionMeta) clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(slotTimeKey, PersistentDataType.LONG)) {

                event.setCancelled(true);

                int stars = CaskQualityEngine.calculateHarvestStars(plugin, clickedItem, physicalWood, barrelBlock);
                String recipeKey = meta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "WORT_BEER");
                BreweryData.RecipeProfile profile = BreweryData.RECIPES.get(recipeKey);

                String finalDrinkName = (profile != null) ? profile.name : "Ruined Stagnant Slop";

                if (stars >= 1) {
                    CaskQualityEngine.incrementBatchCounter(plugin, barrelBlock);
                }

                event.setCurrentItem(null);
                clearCompanionPane(inv, slot);
                saveInventoryToBarrel(barrelBlock, inv);

                giveFinishedBooze(player, stars, finalDrinkName);
                return;
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemStack itemInSlot = inv.getItem(slot);
            if (itemInSlot != null && itemInSlot.getType() == Material.POTION) {
                PotionMeta meta = (PotionMeta) itemInSlot.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(recipeTypeKey, PersistentDataType.STRING)
                        && !meta.getPersistentDataContainer().has(slotTimeKey, PersistentDataType.LONG)) {

                    meta.getPersistentDataContainer().set(slotTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
                    itemInSlot.setItemMeta(meta);
                }
            }
            saveInventoryToBarrel(barrelBlock, inv);
        }, 1L);
    }

    @EventHandler
    public void onUiClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inventorySourceMap.containsKey(inv)) {
            Block barrelBlock = inventorySourceMap.get(inv);

            if (liveVisualTasks.containsKey(inv)) {
                liveVisualTasks.get(inv).cancel();
                liveVisualTasks.remove(inv);
            }

            saveInventoryToBarrel(barrelBlock, inv);
            inventorySourceMap.remove(inv);
        }
    }

    private void startLivePaneUpdater(Inventory inv) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Block barrelBlock = inventorySourceMap.get(inv);
                if (barrelBlock == null) return;
                CaskVisualEngine.updateCaskInterface(plugin, inv, barrelBlock);
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        liveVisualTasks.put(inv, task);
    }

    private void clearCompanionPane(Inventory inv, int bottleSlot) {
        int paneSlot = bottleSlot + 1;
        if (paneSlot < 9) {
            inv.setItem(paneSlot, new ItemStack(Material.AIR));
        }
    }

    private String scanSoftCasingEnvelope(Block barrel) {
        String woodType = null;
        int validComponentCount = 0;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block targetBlock = barrel.getRelative(x, y, z);
                    Material mat = targetBlock.getType();
                    String targetName = mat.name();

                    if (mat == Material.AIR || mat == Material.BARREL || targetName.contains("SIGN")) continue;

                    if (woodType == null) {
                        if (targetName.endsWith("_STAIRS") || targetName.endsWith("_SLAB") || targetName.endsWith("_PLANKS") || targetName.contains("_LOG")) {
                            woodType = BreweryData.getWoodFromBlock(mat);
                        }
                    }

                    if (woodType != null && targetName.contains(woodType)) {
                        if (targetName.endsWith("_STAIRS") || targetName.endsWith("_SLAB") || targetName.endsWith("_PLANKS") || targetName.contains("_LOG")) {
                            validComponentCount++;
                        }
                    }
                }
            }
        }
        if (woodType != null && validComponentCount >= 12) return woodType;
        return "UNKNOWN";
    }

    private Block findAdjacentBarrel(Block signBlock) {
        for (BlockFace face : BlockFace.values()) {
            Block neighbor = signBlock.getRelative(face);
            if (neighbor.getType() == Material.BARREL) return neighbor;
        }
        return null;
    }

    private void saveInventoryToBarrel(Block block, Inventory inv) {
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
            tileState.getPersistentDataContainer().set(barrelStorageKey, PersistentDataType.STRING, serialized);
            tileState.update();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadInventoryFromBarrel(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        String serialized = tileState.getPersistentDataContainer().get(barrelStorageKey, PersistentDataType.STRING);
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

    private void giveFinishedBooze(Player player, int stars, String drinkName) {
        ItemStack alcohol = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) alcohol.getItemMeta();

        if (meta == null) meta = (PotionMeta) Bukkit.getItemFactory().getItemMeta(Material.POTION);
        if (meta != null) {
            String starDisplay = "§e" + "★".repeat(Math.min(stars, 5));

            if (stars == 6) {
                starDisplay = "§d§lMythic ★★★★★★";
            } else if (stars == 1) {
                starDisplay = "§7★ (Meh)";
            } else if (stars == 0) {
                drinkName = "Ruined Slop";
                starDisplay = "§4☠ Completely Ruined";
            }

            meta.setDisplayName((stars > 3 ? "§6" : "§f") + drinkName);
            meta.setLore(List.of(
                    "§7Quality Rating: " + starDisplay,
                    "§7Purity Level: §f" + stars,
                    "",
                    "§d§oRight-click to ingest product contents."
            ));

            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "alcohol_stars"), PersistentDataType.INTEGER, stars);
            alcohol.setItemMeta(meta);
        }

        player.getInventory().addItem(alcohol);
        player.sendMessage("§a[Brewery] Bottled: " + drinkName + " (" + stars + "★)");
    }
}