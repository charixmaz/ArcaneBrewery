package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery.DistilleryBatchMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery.LegacyWortBridge;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientItemAdapter;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.MachineIngredientExtractor;
import me.arcanebrewery.arcaneBrewery.application.distillery.*;

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
import org.bukkit.event.block.BlockBreakEvent;
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
import java.util.UUID;

public class DistilleryMenuHandler implements Listener {
    private static final long PROCESS_DURATION_MILLIS = 10_000L;

    private final JavaPlugin plugin;
    private final NamespacedKey recipeTypeKey;
    private final NamespacedKey purityKey;
    private final NamespacedKey distilleryStorageKey;
    private final NamespacedKey batchStateKey;
    private final Map<Inventory, Block> distillerySourceMap = new HashMap<>();
    private final Map<MachineLocation, BukkitRunnable> activeBoilTasks = new HashMap<>();

    private final MachineIngredientExtractor ingredientExtractor;
    private final DistilleryService distilleryService = new DistilleryService();
    private final DistilleryStateCodec stateCodec = new DistilleryStateCodec();
    private final DistilleryBatchMetadata batchMetadata;
    private final LegacyWortBridge legacyWortBridge = new LegacyWortBridge();

    public DistilleryMenuHandler(JavaPlugin plugin, IngredientItemAdapter ingredientItems) {
        this.plugin = plugin;
        this.recipeTypeKey = new NamespacedKey(plugin, "brew_stage");
        this.purityKey = new NamespacedKey(plugin, "ingredient_purity");
        this.distilleryStorageKey = new NamespacedKey(plugin, "distillery_inventory_storage");
        this.batchStateKey = new NamespacedKey(plugin, "distillery_batch_state");
        this.ingredientExtractor = new MachineIngredientExtractor(ingredientItems);
        this.batchMetadata = new DistilleryBatchMetadata(plugin);
    }


    @EventHandler
    public void onDistilleryBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BREWING_STAND) return;

        DistilleryBatchState state = readState(block);
        boolean processing = state != null && state.status() == DistilleryBatchState.Status.PROCESSING;
        boolean storedItems = false;
        Inventory snapshot = Bukkit.createInventory(null, 9);
        loadDistilleryFromBlock(block, snapshot);
        for (int slot : new int[]{0, 1, 2, 4}) {
            ItemStack item = snapshot.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                storedItems = true;
                break;
            }
        }

        if (processing || storedItems) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[Brewery] Empty the distillery and let any active batch finish before breaking it.");
        }
    }

    @EventHandler
    public void onDistilleryOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BREWING_STAND) return;

        event.setCancelled(true);
        Inventory distilleryGui = Bukkit.createInventory(null, 9, "§8Industrial Core Distillery");
        distillerySourceMap.put(distilleryGui, block);
        loadDistilleryFromBlock(block, distilleryGui);
        recoverBatch(block, distilleryGui);
        setupBaseGuiLayout(distilleryGui, isProcessing(block));
        event.getPlayer().openInventory(distilleryGui);
    }

    @EventHandler
    public void onDistilleryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!event.getView().getTitle().equals("§8Industrial Core Distillery")) return;

        int slot = event.getSlot();
        if (event.getClickedInventory() == inv && slot != 0 && slot != 1 && slot != 2 && slot != 4) {
            event.setCancelled(true);
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Block block = distillerySourceMap.get(inv);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block == null) return;
            saveDistilleryToBlock(block, inv);

            ItemStack ingredient = inv.getItem(4);
            if (ingredient == null || !hasValidBottles(inv)) return;

            boolean phaseTwo = isAttemptingPhaseTwoRefinement(inv);
            int currentPurity = IngredientPurityEngine.getPurity(plugin, ingredient);
            if (currentPurity == 0 && !phaseTwo) {
                player.sendMessage(MessageConfig.get("distillery_wash_first"));
                return;
            }
            if (phaseTwo && ingredient.getType() != Material.GOLDEN_APPLE) {
                player.sendMessage(MessageConfig.get("distillery_needs_catalyst"));
                return;
            }

            if (!isProcessing(block)) {
                try {
                    startDistillation(block, inv);
                } catch (RuntimeException failure) {
                    player.sendMessage("§cThis item cannot be processed by the distillery.");
                    plugin.getLogger().fine("Distillery rejected input: " + failure.getMessage());
                }
            }
        }, 1L);
    }

    @EventHandler
    public void onDistilleryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        Block block = distillerySourceMap.remove(inv);
        if (block != null) saveDistilleryToBlock(block, inv);
    }

    private void startDistillation(Block block, Inventory inv) {
        if (readState(block) != null && readState(block).status() == DistilleryBatchState.Status.PROCESSING) return;
        var extracted = ingredientExtractor.extract(inv, 4);
        var state = distilleryService.start(machineLocation(block), extracted, DistillationControls.DEFAULT,
                System.currentTimeMillis(), PROCESS_DURATION_MILLIS, UUID.randomUUID());
        writeState(block, state);
        schedule(block, state);
    }

    private void recoverBatch(Block block, Inventory inv) {
        DistilleryBatchState state = readState(block);
        if (state == null || state.status() == DistilleryBatchState.Status.COMPLETED) return;
        if (state.ready(System.currentTimeMillis())) {
            completeBatch(block, inv, state);
        } else if (!activeBoilTasks.containsKey(state.machine())) {
            schedule(block, state);
        }
    }

    private void schedule(Block block, DistilleryBatchState state) {
        MachineLocation machine = state.machine();
        if (activeBoilTasks.containsKey(machine)) return;
        updateAllOpenGuisForBlock(block, true);

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                DistilleryBatchState current = readState(block);
                if (current == null || current.status() == DistilleryBatchState.Status.COMPLETED) {
                    activeBoilTasks.remove(machine);
                    cancel();
                    return;
                }

                if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                    block.getWorld().spawnParticle(Particle.SMOKE, block.getLocation().add(0.5, 1.2, 0.5), 4, 0.1, 0.1, 0.1, 0.02);
                    block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1.2f);
                }

                if (current.ready(System.currentTimeMillis())) {
                    Inventory target = openInventoryFor(block);
                    if (target == null) {
                        target = Bukkit.createInventory(null, 9);
                        loadDistilleryFromBlock(block, target);
                    }
                    completeBatch(block, target, current);
                    if (!distillerySourceMap.containsKey(target)) saveDistilleryToBlock(block, target);
                    activeBoilTasks.remove(machine);
                    cancel();
                }
            }
        };
        activeBoilTasks.put(machine, task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    private void completeBatch(Block block, Inventory inv, DistilleryBatchState expected) {
        DistilleryBatchState current = readState(block);
        if (current == null || current.status() == DistilleryBatchState.Status.COMPLETED) return;
        if (!current.batchId().equals(expected.batchId()) || !current.ready(System.currentTimeMillis())) return;

        long completedAt = System.currentTimeMillis();
        DistilleryBatchState completed = current.complete(completedAt);

        if (isAttemptingPhaseTwoRefinement(inv)) {
            executeLegacyRefinement(inv);
        } else {
            // Bottle metadata must carry the terminal batch state. Fermentation
            // intentionally rejects PROCESSING distillery outputs.
            executeCanonicalDistillation(inv, completed);
        }

        writeState(block, completed);
        saveDistilleryToBlock(block, inv);
        setupBaseGuiLayout(inv, false);

        if (!inv.getViewers().isEmpty() && inv.getViewers().getFirst() instanceof Player player) {
            player.sendMessage(MessageConfig.get("distillery_success"));
        }
        if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.8f, 1.2f);
        }
    }

    private void executeCanonicalDistillation(Inventory inv, DistilleryBatchState state) {
        ItemStack ingredient = inv.getItem(4);
        if (ingredient == null) return;

        String legacyStage = legacyWortBridge.stageFor(state.draft());
        double weightedPurity = state.draft().ingredientPurity().entrySet().stream()
                .mapToDouble(entry -> state.draft().ingredientRatios().get(entry.getKey()) * entry.getValue()).sum();

        for (int slot : new int[]{0, 1, 2}) {
            ItemStack flask = inv.getItem(slot);
            if (flask == null) continue;
            if (flask.getType() == Material.GLASS_BOTTLE) flask.setType(Material.POTION);
            if (flask.getType() != Material.POTION) continue;

            PotionMeta meta = (PotionMeta) flask.getItemMeta();
            if (meta == null) meta = (PotionMeta) Bukkit.getItemFactory().getItemMeta(Material.POTION);
            if (meta == null) continue;

            meta.setDisplayName("§fDistilled Brewing Base");
            meta.setLore(List.of(
                    "§7Stage: §bDistilled base",
                    "§7Transferred purity: §e" + Math.round(weightedPurity) + "%",
                    "",
                    "§8Arcane process data stored internally."
            ));
            batchMetadata.write(meta, state, "DISTILLED_BASE");
            meta.getPersistentDataContainer().set(recipeTypeKey, PersistentDataType.STRING, legacyStage);
            meta.getPersistentDataContainer().set(purityKey, PersistentDataType.INTEGER, (int) Math.round(weightedPurity));
            flask.setItemMeta(meta);
        }

        consumeOne(inv, 4);
    }

    private void executeLegacyRefinement(Inventory inv) {
        for (int slot : new int[]{0, 1, 2}) {
            ItemStack flask = inv.getItem(slot);
            if (flask == null || flask.getType() != Material.POTION) continue;
            PotionMeta meta = (PotionMeta) flask.getItemMeta();
            if (meta == null) continue;
            meta.setDisplayName("§dRefined Industrial Alcohol");
            meta.setLore(List.of("§7Type: §fConcentrated Chemical Catalyst", "§7Purity Level: §aHigh-Grade Compound", "", "§e[Refinery Producer Required]"));
            meta.getPersistentDataContainer().set(recipeTypeKey, PersistentDataType.STRING, "REFINED_INDUSTRIAL_ALCOHOL");
            flask.setItemMeta(meta);
        }
        consumeOne(inv, 4);
    }

    private void consumeOne(Inventory inv, int slot) {
        ItemStack ingredient = inv.getItem(slot);
        if (ingredient == null) return;
        if (ingredient.getAmount() > 1) ingredient.setAmount(ingredient.getAmount() - 1);
        else inv.setItem(slot, null);
    }

    private DistilleryBatchState readState(Block block) {
        if (!(block.getState() instanceof TileState tile)) return null;
        String encoded = tile.getPersistentDataContainer().get(batchStateKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) return null;
        try {
            DistilleryBatchState state = stateCodec.decodeBase64(encoded);
            return state.machine().equals(machineLocation(block)) ? state : null;
        } catch (IllegalArgumentException failure) {
            plugin.getLogger().warning("Ignoring corrupt distillery state at " + block.getLocation());
            return null;
        }
    }

    private void writeState(Block block, DistilleryBatchState state) {
        if (!(block.getState() instanceof TileState tile)) return;
        tile.getPersistentDataContainer().set(batchStateKey, PersistentDataType.STRING, stateCodec.encodeBase64(state));
        tile.update();
    }

    private MachineLocation machineLocation(Block block) {
        return new MachineLocation(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }

    private boolean isProcessing(Block block) {
        DistilleryBatchState state = readState(block);
        return state != null && state.status() == DistilleryBatchState.Status.PROCESSING;
    }

    private Inventory openInventoryFor(Block block) {
        for (var entry : distillerySourceMap.entrySet()) {
            if (entry.getValue().equals(block)) return entry.getKey();
        }
        return null;
    }

    private boolean hasValidBottles(Inventory inv) {
        for (int slot : new int[]{0, 1, 2}) {
            ItemStack item = inv.getItem(slot);
            if (item != null && (item.getType() == Material.POTION || item.getType() == Material.GLASS_BOTTLE)) return true;
        }
        return false;
    }

    private boolean isAttemptingPhaseTwoRefinement(Inventory inv) {
        for (int slot : new int[]{0, 1, 2}) {
            ItemStack flask = inv.getItem(slot);
            if (flask != null && flask.getType() == Material.POTION && flask.hasItemMeta()) {
                PotionMeta pMeta = (PotionMeta) flask.getItemMeta();
                if (!batchMetadata.stage(pMeta).isBlank()) continue;
                String stage = pMeta.getPersistentDataContainer().getOrDefault(recipeTypeKey, PersistentDataType.STRING, "");
                if (stage.equalsIgnoreCase("WORT_ETHANOL")) return true;
            }
        }
        return false;
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

    private void updateAllOpenGuisForBlock(Block block, boolean isBoiling) {
        for (Map.Entry<Inventory, Block> entry : distillerySourceMap.entrySet()) {
            if (entry.getValue().equals(block)) setupBaseGuiLayout(entry.getKey(), isBoiling);
        }
    }

    private boolean isValidStorageItem(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.POTION || type == Material.GLASS_BOTTLE || type.isEdible()
                || type == Material.WHEAT || type == Material.SUGAR_CANE || type == Material.HONEYCOMB;
    }

    private void saveDistilleryToBlock(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
                dataOutput.writeInt(9);
                for (int i = 0; i < 9; i++) {
                    ItemStack item = inv.getItem(i);
                    boolean editableSlot = i == 0 || i == 1 || i == 2 || i == 4;
                    dataOutput.writeObject(editableSlot && item != null ? item : null);
                }
            }
            String serialized = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            tileState.getPersistentDataContainer().set(distilleryStorageKey, PersistentDataType.STRING, serialized);
            tileState.update();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to persist legacy distillery inventory: " + e.getMessage());
        }
    }

    private void loadDistilleryFromBlock(Block block, Inventory inv) {
        if (!(block.getState() instanceof TileState tileState)) return;
        String serialized = tileState.getPersistentDataContainer().get(distilleryStorageKey, PersistentDataType.STRING);
        if (serialized == null || serialized.isEmpty()) return;
        try {
            byte[] bytes = Base64.getDecoder().decode(serialized);
            if (bytes.length > 1_048_576) throw new IllegalArgumentException("legacy distillery inventory too large");
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
                int size = Math.min(9, dataInput.readInt());
                for (int i = 0; i < size; i++) {
                    ItemStack loaded = (ItemStack) dataInput.readObject();
                    if ((i == 0 || i == 1 || i == 2 || i == 4) && loaded != null) {
                        inv.setItem(i, loaded);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ignoring invalid legacy distillery inventory: " + e.getMessage());
        }
    }
}
