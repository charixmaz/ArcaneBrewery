package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyPreparationMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.*;

public final class AlchemyMachineHandler implements Listener {
    private static final String TITLE = "§5Arcane Infusion Crucible";
    private static final long PROCESS_DURATION_MILLIS = 8_000L;
    private static final int BASE_SLOT = 2;
    private static final int CATALYST_A = 4;
    private static final int CATALYST_B = 6;
    private static final int START_SLOT = 8;

    private final JavaPlugin plugin;
    private final FermentationBatchMetadata fermentation;
    private final AlchemyPreparationMetadata preparation;
    private final AlchemyMachineService service = new AlchemyMachineService();
    private final NamespacedKey stateFormatKey;
    private final NamespacedKey stateBatchKey;
    private final NamespacedKey stateStartKey;
    private final NamespacedKey stateDurationKey;
    private final NamespacedKey stateStatusKey;
    private final NamespacedKey inventoryKey;

    private final Map<Inventory, Block> sources = new HashMap<>();
    private final Map<String, BukkitRunnable> activeTasks = new HashMap<>();

    public AlchemyMachineHandler(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.fermentation = new FermentationBatchMetadata(plugin);
        this.preparation = new AlchemyPreparationMetadata(plugin);
        this.stateFormatKey = new NamespacedKey(plugin, "alchemy_machine_format");
        this.stateBatchKey = new NamespacedKey(plugin, "alchemy_machine_batch");
        this.stateStartKey = new NamespacedKey(plugin, "alchemy_machine_started");
        this.stateDurationKey = new NamespacedKey(plugin, "alchemy_machine_duration");
        this.stateStatusKey = new NamespacedKey(plugin, "alchemy_machine_status");
        this.inventoryKey = new NamespacedKey(plugin, "alchemy_machine_inventory");
    }

    @EventHandler
    public void onOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block core = event.getClickedBlock();
        if (core == null || core.getType() != Material.ENCHANTING_TABLE) return;

        event.setCancelled(true);
        if (!AlchemyStructureScanner.isValid(core)) {
            event.getPlayer().sendMessage("§c[Alchemy] Invalid crucible structure.");
            event.getPlayer().sendMessage("§7Requires Crying Obsidian below and an adjacent Amethyst Block.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 9, TITLE);
        sources.put(gui, core);
        loadInventory(core, gui);
        recover(core, gui);
        paint(gui, isProcessing(core));
        event.getPlayer().openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory gui = event.getInventory();
        if (!TITLE.equals(event.getView().getTitle())) return;
        Block core = sources.get(gui);
        if (core == null) return;

        int slot = event.getSlot();
        boolean top = event.getClickedInventory() == gui;
        boolean processing = isProcessing(core);

        if (processing && event.isShiftClick()) {
            event.setCancelled(true);
            ((Player) event.getWhoClicked()).sendMessage("§c[Alchemy] Inputs are locked while infusing.");
            return;
        }

        if (top && slot == START_SLOT) {
            event.setCancelled(true);
            if (processing) {
                ((Player) event.getWhoClicked()).sendMessage("§e[Alchemy] Infusion already in progress.");
                return;
            }
            start((Player) event.getWhoClicked(), core, gui);
            return;
        }

        if (top && slot != BASE_SLOT && slot != CATALYST_A && slot != CATALYST_B) {
            event.setCancelled(true);
            return;
        }

        if (processing && top && (slot == BASE_SLOT || slot == CATALYST_A || slot == CATALYST_B)) {
            event.setCancelled(true);
            ((Player) event.getWhoClicked()).sendMessage("§c[Alchemy] Inputs are locked while infusing.");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            paint(gui, isProcessing(core));
            saveInventory(core, gui);
        }, 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory gui = event.getInventory();
        Block core = sources.remove(gui);
        if (core != null) saveInventory(core, gui);
    }

    private void start(Player player, Block core, Inventory gui) {
        try {
            ItemStack base = gui.getItem(BASE_SLOT);
            if (base == null || base.getType() != Material.POTION || !(base.getItemMeta() instanceof PotionMeta meta)) {
                player.sendMessage("§c[Alchemy] Slot 2 requires a completed fermented base.");
                return;
            }

            var fermented = fermentation.read(meta);
            if (fermented.isEmpty() || fermented.get().status() != FermentationBatchState.Status.COMPLETED) {
                player.sendMessage("§c[Alchemy] The base has not completed fermentation.");
                return;
            }
            if (preparation.read(meta).isPresent()) {
                player.sendMessage("§c[Alchemy] This base was already infused.");
                return;
            }

            List<AlchemyCatalyst> catalysts = catalysts(gui);
            if (catalysts.isEmpty()) {
                player.sendMessage("§c[Alchemy] Add at least one supported catalyst.");
                return;
            }

            double preview = service.previewInstability(catalysts);
            AlchemyMachineState state = service.start(
                    System.currentTimeMillis(), PROCESS_DURATION_MILLIS, UUID.randomUUID());
            writeState(core, state);
            saveInventory(core, gui);
            paint(gui, true);
            schedule(core, state);
            player.sendMessage("§d[Alchemy] Infusion started. Instability estimate: §f"
                    + Math.round(preview) + "/100");
        } catch (RuntimeException failure) {
            player.sendMessage("§c[Alchemy] Unable to start infusion: " + failure.getMessage());
        }
    }

    private void recover(Block core, Inventory gui) {
        AlchemyMachineState state = readState(core);
        if (state == null || state.status() == AlchemyMachineState.Status.COMPLETED) return;
        if (state.ready(System.currentTimeMillis())) complete(core, gui, state);
        else schedule(core, state);
    }

    private void schedule(Block core, AlchemyMachineState state) {
        String key = machineKey(core);
        if (activeTasks.containsKey(key)) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                AlchemyMachineState current = readState(core);
                if (current == null || current.status() == AlchemyMachineState.Status.COMPLETED) {
                    activeTasks.remove(key);
                    cancel();
                    return;
                }

                if (core.getWorld().isChunkLoaded(core.getX() >> 4, core.getZ() >> 4)) {
                    core.getWorld().spawnParticle(Particle.ENCHANT, core.getLocation().add(0.5, 1.0, 0.5),
                            10, 0.35, 0.25, 0.35, 0.05);
                    core.getWorld().playSound(core.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.1f);
                }

                if (current.ready(System.currentTimeMillis())) {
                    Inventory gui = openInventory(core);
                    if (gui == null) {
                        gui = Bukkit.createInventory(null, 9, TITLE);
                        loadInventory(core, gui);
                    }
                    complete(core, gui, current);
                    if (!sources.containsKey(gui)) saveInventory(core, gui);
                    activeTasks.remove(key);
                    cancel();
                }
            }
        };
        activeTasks.put(key, task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    private void complete(Block core, Inventory gui, AlchemyMachineState expected) {
        AlchemyMachineState current = readState(core);
        if (current == null || current.status() == AlchemyMachineState.Status.COMPLETED) return;
        if (!current.batchId().equals(expected.batchId()) || !current.ready(System.currentTimeMillis())) return;

        ItemStack base = gui.getItem(BASE_SLOT);
        if (base == null || base.getType() != Material.POTION || !(base.getItemMeta() instanceof PotionMeta meta)) return;

        if (preparation.read(meta).isPresent()) {
            writeState(core, current.complete(System.currentTimeMillis()));
            paint(gui, false);
            return;
        }

        List<AlchemyCatalyst> catalysts = catalysts(gui);
        if (catalysts.isEmpty()) return;

        AlchemyPreparation prepared = service.combine(catalysts);
        preparation.write(meta, prepared);

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("§dArcane Infusion: §f" + prepared.additives().size() + " catalyst type(s)");
        meta.setLore(lore);
        base.setItemMeta(meta);

        consumeOne(gui, CATALYST_A);
        consumeOne(gui, CATALYST_B);
        saveInventory(core, gui);
        writeState(core, current.complete(System.currentTimeMillis()));
        paint(gui, false);

        for (var viewer : gui.getViewers()) {
            if (viewer instanceof Player player) {
                player.sendMessage("§d[Alchemy] Infusion complete. The base is ready for cask aging.");
            }
        }
    }

    private List<AlchemyCatalyst> catalysts(Inventory gui) {
        var result = new ArrayList<AlchemyCatalyst>();
        for (int slot : new int[]{CATALYST_A, CATALYST_B}) {
            ItemStack item = gui.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            String external = "minecraft:" + item.getType().name().toLowerCase(Locale.ROOT);
            var catalyst = BuiltInAlchemyCatalysts.resolveExternal(external);
            if (catalyst.isEmpty()) throw new IllegalArgumentException("unsupported catalyst in slot " + slot);
            result.add(catalyst.get());
        }
        return List.copyOf(result);
    }

    private void consumeOne(Inventory gui, int slot) {
        ItemStack item = gui.getItem(slot);
        if (item == null) return;
        if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
        else gui.setItem(slot, null);
    }

    private void paint(Inventory gui, boolean processing) {
        for (int i = 0; i < 9; i++) {
            if (i == BASE_SLOT || i == CATALYST_A || i == CATALYST_B) continue;
            Material material = i == START_SLOT
                    ? (processing ? Material.CLOCK : Material.NETHER_STAR)
                    : (processing ? Material.PURPLE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE);
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (i == START_SLOT) {
                    meta.setDisplayName(processing ? "§eInfusion in progress" : "§d§lBegin Arcane Infusion");
                    meta.setLore(List.of(processing
                            ? "§7Inputs are locked until completion."
                            : "§7Base: slot 2 §8| §7Catalysts: slots 4 and 6"));
                } else {
                    meta.setDisplayName("§5Arcane Conduit");
                }
                item.setItemMeta(meta);
            }
            gui.setItem(i, item);
        }
    }

    private boolean isProcessing(Block core) {
        AlchemyMachineState state = readState(core);
        return state != null && state.status() == AlchemyMachineState.Status.PROCESSING;
    }

    private void writeState(Block core, AlchemyMachineState state) {
        if (!(core.getState() instanceof TileState tile)) return;
        var pdc = tile.getPersistentDataContainer();
        pdc.set(stateFormatKey, PersistentDataType.INTEGER, AlchemyMachineState.VERSION);
        pdc.set(stateBatchKey, PersistentDataType.STRING, state.batchId().toString());
        pdc.set(stateStartKey, PersistentDataType.LONG, state.startedAtEpochMillis());
        pdc.set(stateDurationKey, PersistentDataType.LONG, state.durationMillis());
        pdc.set(stateStatusKey, PersistentDataType.STRING, state.status().name());
        tile.update();
    }

    private AlchemyMachineState readState(Block core) {
        if (!(core.getState() instanceof TileState tile)) return null;
        var pdc = tile.getPersistentDataContainer();
        Integer version = pdc.get(stateFormatKey, PersistentDataType.INTEGER);
        if (version == null) return null;
        try {
            String batch = pdc.get(stateBatchKey, PersistentDataType.STRING);
            Long start = pdc.get(stateStartKey, PersistentDataType.LONG);
            Long duration = pdc.get(stateDurationKey, PersistentDataType.LONG);
            String status = pdc.get(stateStatusKey, PersistentDataType.STRING);
            if (batch == null || start == null || duration == null || status == null)
                throw new IllegalArgumentException("incomplete alchemy machine state");
            return new AlchemyMachineState(version, UUID.fromString(batch), start, duration,
                    AlchemyMachineState.Status.valueOf(status));
        } catch (RuntimeException malformed) {
            plugin.getLogger().warning("Ignoring corrupt alchemy machine state at " + core.getLocation());
            return null;
        }
    }

    private Inventory openInventory(Block core) {
        for (var entry : sources.entrySet()) {
            if (entry.getValue().equals(core)) return entry.getKey();
        }
        return null;
    }

    private String machineKey(Block core) {
        return core.getWorld().getUID() + ":" + core.getX() + ":" + core.getY() + ":" + core.getZ();
    }

    private void saveInventory(Block core, Inventory gui) {
        if (!(core.getState() instanceof TileState tile)) return;
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new BukkitObjectOutputStream(bytes)) {
                out.writeInt(3);
                out.writeObject(gui.getItem(BASE_SLOT));
                out.writeObject(gui.getItem(CATALYST_A));
                out.writeObject(gui.getItem(CATALYST_B));
            }
            byte[] raw = bytes.toByteArray();
            if (raw.length > 1_048_576) throw new IllegalArgumentException("alchemy inventory too large");
            tile.getPersistentDataContainer().set(inventoryKey, PersistentDataType.STRING,
                    Base64.getEncoder().encodeToString(raw));
            tile.update();
        } catch (Exception failure) {
            plugin.getLogger().warning("Failed to save alchemy inventory: " + failure.getMessage());
        }
    }

    private void loadInventory(Block core, Inventory gui) {
        if (!(core.getState() instanceof TileState tile)) return;
        String encoded = tile.getPersistentDataContainer().get(inventoryKey, PersistentDataType.STRING);
        if (encoded == null || encoded.isBlank()) return;
        try {
            byte[] raw = Base64.getDecoder().decode(encoded);
            if (raw.length > 1_048_576) throw new IllegalArgumentException("alchemy inventory too large");
            try (var in = new BukkitObjectInputStream(new ByteArrayInputStream(raw))) {
                int count = in.readInt();
                if (count != 3) throw new IllegalArgumentException("invalid alchemy inventory format");
                gui.setItem(BASE_SLOT, (ItemStack) in.readObject());
                gui.setItem(CATALYST_A, (ItemStack) in.readObject());
                gui.setItem(CATALYST_B, (ItemStack) in.readObject());
            }
        } catch (Exception failure) {
            plugin.getLogger().warning("Ignoring invalid alchemy inventory: " + failure.getMessage());
        }
    }
}
