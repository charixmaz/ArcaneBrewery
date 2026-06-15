package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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

public class DistilleryMenuHandler implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey recipeTypeKey;
    private final NamespacedKey purityKey;
    private final NamespacedKey distilleryStorageKey;

    private final Map<Inventory, Block> distillerySourceMap = new HashMap<>();
    private final Map<Block, BukkitRunnable> activeBoilTasks = new HashMap<>();

    public DistilleryMenuHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.recipeTypeKey = new NamespacedKey(plugin, "brew_stage");
        this.purityKey = new NamespacedKey(plugin, "ingredient_purity");
        this.distilleryStorageKey = new NamespacedKey(plugin, "distillery_inventory_storage");
    }

    @EventHandler
    public void onDistilleryOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BREWING_STAND) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        Inventory distilleryGui = Bukkit.createInventory(null, 9, "§8Industrial Core Distillery");

        distillerySourceMap.put(distilleryGui, block);
        loadDistilleryFromBlock(block, distilleryGui);
        setupBaseGuiLayout(distilleryGui, activeBoilTasks.containsKey(block));

        player.openInventory(distilleryGui);
    }

    private void setupBaseGuiLayout(Inventory inv, boolean isBoiling) {
        for (int i = 0; i < 9; i++) {
            if (i != 0 && i != 1 && i != 2 && i != 4) {
                ItemStack glass = new ItemStack(isBoiling ? Material.ORANGE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = glass.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(isBoiling ? "§c§l🔥 BOILER LINES RUNNING 🔥" : "§8⚙ Internal Boiler Wall");
                    if (isBoiling) meta.setLore(List.of("§7Status: §6Active Distillation...", "§7Do not retrieve ingredients."));
                    glass.setItemMeta(meta);
                }
                inv.setItem(i, glass);
            }
        }
    }

    @EventHandler
    public void onDistilleryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!event.getView().getTitle().equals("§8Industrial Core Distillery")) return;

        int slot = event.getSlot();
        if (event.getClickedInventory() == inv) {
            if (slot != 0 && slot != 1 && slot != 2 && slot != 4) {
                event.setCancelled(true);
                return;
            }
        }

        Player player = (Player) event.getWhoClicked();
        Block block = distillerySourceMap.get(inv);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block != null) saveDistilleryToBlock(block, inv);

            ItemStack ingredient = inv.getItem(4);
            if (ingredient != null && hasValidBottles(inv)) {
                ItemMeta ingMeta = ingredient.getItemMeta();
                boolean attemptingPhaseTwo = isAttemptingPhaseTwoRefinement(inv);

                int currentPurity = IngredientPurityEngine.getPurity(plugin, ingredient);

                if (currentPurity == 0) {
                    if (!attemptingPhaseTwo) {
                        player.sendMessage(MessageConfig.get("distillery_wash_first"));
                        return;
                    }
                }

                if (attemptingPhaseTwo && ingredient.getType() != Material.GOLDEN_APPLE) {
                    player.sendMessage(MessageConfig.get("distillery_needs_catalyst"));
                    return;
                }

                if (!activeBoilTasks.containsKey(block)) {
                    startOfflineDistillation(block);
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onDistilleryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!distillerySourceMap.containsKey(inv)) return;

        Block block = distillerySourceMap.get(inv);
        saveDistilleryToBlock(block, inv);
        distillerySourceMap.remove(inv);
    }

    private boolean hasValidBottles(Inventory inv) {
        for (int slot : new int[]{0, 1, 2}) {
            ItemStack item = inv.getItem(slot);
            if (item != null && (item.getType() == Material.POTION || item.getType() == Material.GLASS_BOTTLE)) return true;
        }
        return false;
    }

    private void startOfflineDistillation(Block block) {
        updateAllOpenGuisForBlock(block, true);

        BukkitRunnable task = new BukkitRunnable() {
            int timerTicks = 0;

            @Override
            public void run() {
                timerTicks++;

                if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    block.getWorld().spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 1.2, 0.5), 4, 0.1, 0.1, 0.1, 0.02);
                    if (timerTicks % 2 == 0) block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1.2f);
                }

                if (timerTicks >= 10) {
                    boolean guiWasOpen = false;

                    // ISSUE 1 FIX: Prioritize mutating the LIVE inventory first to prevent the infinite exploit!
                    for (Map.Entry<Inventory, Block> entry : distillerySourceMap.entrySet()) {
                        if (entry.getValue().equals(block)) {
                            Inventory openInv = entry.getKey();
                            executeRefineryTransformation(openInv);
                            saveDistilleryToBlock(block, openInv);
                            setupBaseGuiLayout(openInv, false);

                            if (openInv.getViewers().size() > 0) {
                                ((Player) openInv.getViewers().get(0)).sendMessage(MessageConfig.get("distillery_success"));
                            }
                            guiWasOpen = true;
                        }
                    }

                    // If no one was watching, mutate the offline NBT storage normally
                    if (!guiWasOpen) {
                        Inventory offlineInv = Bukkit.createInventory(null, 9);
                        loadDistilleryFromBlock(block, offlineInv);
                        executeRefineryTransformation(offlineInv);
                        saveDistilleryToBlock(block, offlineInv);
                    }

                    if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
                    }

                    activeBoilTasks.remove(block);
                    this.cancel();
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 20L);
        activeBoilTasks.put(block, task);
    }

    private void updateAllOpenGuisForBlock(Block block, boolean isBoiling) {
        for (Map.Entry<Inventory, Block> entry : distillerySourceMap.entrySet()) {
            if (entry.getValue().equals(block)) {
                Inventory openInv = entry.getKey();
                loadDistilleryFromBlock(block, openInv);
                setupBaseGuiLayout(openInv, isBoiling);
            }
        }
    }

    private boolean isAttemptingPhaseTwoRefinement(Inventory inv) {
        for (int slot : new int[]{0, 1, 2}) {
            ItemStack flask = inv.getItem(slot);
            if (flask != null && flask.getType() == Material.POTION && flask.hasItemMeta()) {
                PotionMeta pMeta = (PotionMeta) flask.getItemMeta();
                String stage = pMeta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "");
                if (stage.equalsIgnoreCase("WORT_ETHANOL")) return true;
            }
        }
        return false;
    }

    private void executeRefineryTransformation(Inventory inv) {
        ItemStack ingredient = inv.getItem(4);
        if (ingredient == null) return;

        boolean runningRefinement = isAttemptingPhaseTwoRefinement(inv);
        int ingredientPurity = IngredientPurityEngine.getPurity(plugin, ingredient);

        Material ingType = ingredient.getType();
        String targetStageKey;
        String targetDisplayName;

        switch (ingType) {
            case WHEAT: targetStageKey = "WORT_BEER"; targetDisplayName = "§fUnrefined Beer Wort"; break;
            case SUGAR_CANE: targetStageKey = "WORT_RUM"; targetDisplayName = "§fUnrefined Rum Wort"; break;
            case APPLE: targetStageKey = "WORT_CIDER"; targetDisplayName = "§fUnrefined Cider Wort"; break;
            case POTATO: targetStageKey = "WORT_VODKA"; targetDisplayName = "§fUnrefined Vodka Wort"; break;
            case SWEET_BERRIES: targetStageKey = "WORT_WINE"; targetDisplayName = "§fUnrefined Wine Wort"; break;
            case HONEYCOMB: targetStageKey = "WORT_MEAD"; targetDisplayName = "§fUnrefined Mead Wort"; break;
            case GOLDEN_APPLE:
            default: targetStageKey = "WORT_ETHANOL"; targetDisplayName = "§fUnrefined Ethanol Wort"; break;
        }

        for (int i : new int[]{0, 1, 2}) {
            ItemStack flask = inv.getItem(i);
            if (flask == null) continue;

            if (flask.getType() == Material.GLASS_BOTTLE) flask.setType(Material.POTION);
            if (flask.getType() != Material.POTION) continue;

            PotionMeta pMeta = (PotionMeta) flask.getItemMeta();
            if (pMeta == null) pMeta = (PotionMeta) Bukkit.getItemFactory().getItemMeta(Material.POTION);
            if (pMeta == null) continue;

            if (runningRefinement) {
                pMeta.setDisplayName("§dRefined Industrial Alcohol");
                pMeta.setLore(List.of("§7Type: §fConcentrated Chemical Catalyst", "§7Purity Level: §aHigh-Grade Compound", "", "§e[Refinery Producer Required]"));
                pMeta.getPersistentDataContainer().set(recipeTypeKey, PersistentDataType.STRING, "REFINED_INDUSTRIAL_ALCOHOL");
            } else {
                pMeta.setDisplayName(targetDisplayName);
                pMeta.setLore(List.of("§7Type: §fRaw Distillation Base", "§7Fermentation Stage: §bWort", "§7Base Purity Transfer: §e" + ingredientPurity + "%", "", "§4⚠ TOXIC GAS DEVELOPING: DO NOT INGEST"));
                pMeta.getPersistentDataContainer().set(recipeTypeKey, PersistentDataType.STRING, targetStageKey);
                pMeta.getPersistentDataContainer().set(purityKey, PersistentDataType.INTEGER, ingredientPurity);
            }
            flask.setItemMeta(pMeta);
        }

        // Deduct exactly 1 to patch the infinite processing exploit
        if (ingredient.getAmount() > 1) {
            ingredient.setAmount(ingredient.getAmount() - 1);
        } else {
            inv.setItem(4, null);
        }
    }

    private boolean isValidStorageItem(ItemStack item) {
        if (item == null) return false;
        Material t = item.getType();
        return t == Material.POTION || t == Material.GLASS_BOTTLE || t.isEdible() || t == Material.WHEAT || t == Material.SUGAR_CANE || t == Material.HONEYCOMB;
    }

    private void saveDistilleryToBlock(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(9);
            for (int i = 0; i < 9; i++) {
                ItemStack item = inv.getItem(i);
                if (isValidStorageItem(item)) dataOutput.writeObject(item);
                else dataOutput.writeObject(null);
            }
            dataOutput.close();
            String serialized = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            tileState.getPersistentDataContainer().set(distilleryStorageKey, PersistentDataType.STRING, serialized);
            tileState.update();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadDistilleryFromBlock(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        String serialized = tileState.getPersistentDataContainer().get(distilleryStorageKey, PersistentDataType.STRING);
        if (serialized == null || serialized.isEmpty()) return;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(serialized));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            for (int i = 0; i < size; i++) {
                ItemStack loaded = (ItemStack) dataInput.readObject();
                if (isValidStorageItem(loaded)) inv.setItem(i, loaded);
            }
            dataInput.close();
        } catch (Exception e) { e.printStackTrace(); }
    }
}