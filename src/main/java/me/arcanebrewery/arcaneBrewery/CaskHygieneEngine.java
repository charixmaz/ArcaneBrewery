package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class CaskHygieneEngine implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey contaminationKey;
    private final NamespacedKey spoilTimeKey;
    private final NamespacedKey starRatingKey;

    public CaskHygieneEngine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.contaminationKey = new NamespacedKey(plugin, "cask_contaminated");
        this.spoilTimeKey = new NamespacedKey(plugin, "cask_spoil_timestamp");
        this.starRatingKey = new NamespacedKey(plugin, "alcohol_stars");
    }

    /**
     * Evaluates if a brewing cask or producer has passed its 10-minute contamination fuse window.
     */
    public static boolean checkInfectionStatus(JavaPlugin plugin, Block barrelBlock) {
        if (barrelBlock == null || !(barrelBlock.getState() instanceof TileState tileState)) return false;

        NamespacedKey contKey = new NamespacedKey(plugin, "cask_contaminated");
        NamespacedKey spoilKey = new NamespacedKey(plugin, "cask_spoil_timestamp");

        if (tileState.getPersistentDataContainer().getOrDefault(contKey, PersistentDataType.INTEGER, 0) == 1) {
            return true;
        }

        if (tileState.getPersistentDataContainer().has(spoilKey, PersistentDataType.LONG)) {
            long spoilStart = tileState.getPersistentDataContainer().getOrDefault(spoilKey, PersistentDataType.LONG, System.currentTimeMillis());
            long elapsedSeconds = (System.currentTimeMillis() - spoilStart) / 1000;

            // FUSE BLOWS AT 10 MINUTES (600 Seconds)
            if (elapsedSeconds >= 600) {
                tileState.getPersistentDataContainer().set(contKey, PersistentDataType.INTEGER, 1);

                // FIXED: Instantiated locally and dynamically to prevent early initialization crashes!
                NamespacedKey storageKey = new NamespacedKey(plugin, "barrel_inventory_storage");
                tileState.getPersistentDataContainer().remove(storageKey);
                tileState.update();

                // Play custom fungal collapse effects
                barrelBlock.getWorld().playSound(barrelBlock.getLocation(), Sound.BLOCK_SLIME_BLOCK_BREAK, 1.5f, 0.5f);
                barrelBlock.getWorld().spawnParticle(Particle.POOF, barrelBlock.getLocation().add(0.5, 1.0, 0.5), 30, 0.2, 0.2, 0.2, 0.02);

                // Spawn physical outbreak evidence on top of the block core
                Block topBlock = barrelBlock.getRelative(0, 1, 0);
                if (topBlock.getType() == Material.AIR) {
                    topBlock.setType(Material.BROWN_MUSHROOM);
                }

                return true;
            }
        }
        return false;
    }

    /**
     * Sweeps a 5x5x5 perimeter around the machine core and pushes real-time text layouts
     * to both wooden aging Casks and your new industrial Methanol Producers!
     */
    public static void updateExternalSigns(JavaPlugin plugin, Block barrelBlock) {
        if (barrelBlock == null || !(barrelBlock.getState() instanceof TileState tileState)) return;

        NamespacedKey contKey = new NamespacedKey(plugin, "cask_contaminated");
        NamespacedKey spoilKey = new NamespacedKey(plugin, "cask_spoil_timestamp");

        boolean isInfected = tileState.getPersistentDataContainer().getOrDefault(contKey, PersistentDataType.INTEGER, 0) == 1;
        long spoilStart = tileState.getPersistentDataContainer().getOrDefault(spoilKey, PersistentDataType.LONG, -1L);

        long remainSeconds = 600;
        if (spoilStart != -1L) {
            remainSeconds = 600 - ((System.currentTimeMillis() - spoilStart) / 1000);
            if (remainSeconds <= 0) isInfected = true;
        }

        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block checkBlock = barrelBlock.getRelative(x, y, z);
                    if (checkBlock.getState() instanceof Sign sign) {
                        SignSide frontSide = sign.getSide(Side.FRONT);
                        String firstLine = frontSide.getLine(0);

                        // INTEGRATED HOOKS: Detects both standard diagnostic formats, cut-off tags, and the new industrial producer setups
                        if (firstLine.contains("[Status]") || firstLine.contains("[Stats]") || firstLine.contains("[Cask") || firstLine.contains("Diagnostics") || firstLine.contains("[Producer]") || firstLine.contains("Refinery")) {

                            if (isInfected) {
                                // If the machine is an industrial producer structure
                                if (firstLine.contains("[Producer]") || firstLine.contains("Refinery")) {
                                    frontSide.setLine(0, "§4§l☣ REFINERY LOCK");
                                    frontSide.setLine(1, "§cPIPES CLOGGED");
                                    frontSide.setLine(2, "§7Needs Core Cleanse");
                                    frontSide.setLine(3, "§7Immediately!");
                                } else { // Standard aging cask structure
                                    frontSide.setLine(0, "§4§l☣ INFECTED ☣");
                                    frontSide.setLine(1, "§cSTATUS: TOXIC");
                                    frontSide.setLine(2, "§7Needs 4★+");
                                    frontSide.setLine(3, "§7Sanitizer!");
                                }
                            } else if (spoilStart != -1L) {
                                frontSide.setLine(0, "§4§l[CRITICAL]");
                                frontSide.setLine(1, "§cMOLD EXPIRY:");
                                frontSide.setLine(2, "§f" + Math.max(0, remainSeconds) + "s");
                                frontSide.setLine(3, "§7Extract Slop!");
                            } else {
                                // Dynamic active state branching layouts
                                if (firstLine.contains("[Producer]") || firstLine.contains("Refinery")) {
                                    frontSide.setLine(0, "§8§l[Refinery Stats]");
                                    frontSide.setLine(1, "§aUNIT: OPERATIONAL");
                                    frontSide.setLine(2, "§7Methanol: §b99.8%");
                                    frontSide.setLine(3, "§7Pressure: §f1.2 atm");
                                } else {
                                    frontSide.setLine(0, "§2§l[Diagnostics]");
                                    frontSide.setLine(1, "§aSTATUS: PURE");
                                    frontSide.setLine(2, "§7Atmosphere OK");
                                    frontSide.setLine(3, "§7✔ READY");
                                }
                            }
                            sign.update();
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles right-clicking infected machinery with 4★+ high-proof custom alcohol
     * to sanitize the casing, remove structural mutations, and flush background NBT storage.
     */
    @EventHandler
    public void onCaskSanitize(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Supports cleaning out either the organic Aging barrels or your industrial Gray Concrete refinery hubs
        if (clicked.getType() != Material.BARREL && clicked.getType() != Material.GRAY_CONCRETE) return;

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem.getType() != Material.POTION || !heldItem.hasItemMeta()) return;

        TileState tileState = (TileState) clicked.getState();
        if (tileState.getPersistentDataContainer().getOrDefault(contaminationKey, PersistentDataType.INTEGER, 0) == 0) {
            return; // Casing structure is already fully sterile
        }

        PotionMeta meta = (PotionMeta) heldItem.getItemMeta();
        if (!meta.getPersistentDataContainer().has(starRatingKey, PersistentDataType.INTEGER)) return;

        int qualityStars = meta.getPersistentDataContainer().getOrDefault(starRatingKey, PersistentDataType.INTEGER, 0);

        // HARDCORE MINIMUM PROOF CEILING: Rejects cheap 1-3 star variants
        if (qualityStars < 4) {
            player.sendMessage("§c[Brewery] This alcohol proof is too low! You need a 4★+ high-purity brew to dissolve the contamination.");
            return;
        }

        event.setCancelled(true);

        // Reset infection state mappings out of data arrays
        tileState.getPersistentDataContainer().set(contaminationKey, PersistentDataType.INTEGER, 0);
        tileState.getPersistentDataContainer().remove(spoilTimeKey);
        tileState.update();

        // High-contrast chemical purification feedbacks
        clicked.getWorld().playSound(clicked.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.8f);
        clicked.getWorld().playSound(clicked.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.1f);
        clicked.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, clicked.getLocation().add(0.5, 1.1, 0.5), 25, 0.3, 0.3, 0.3, 0.05);

        // Erase physical mushroom buildup overhead upon processing sterilization fluids
        Block topBlock = clicked.getRelative(0, 1, 0);
        if (topBlock.getType() == Material.BROWN_MUSHROOM) {
            topBlock.setType(Material.AIR);
        }

        // Return empty container shell to player main hand layout
        if (heldItem.getAmount() > 1) {
            heldItem.setAmount(heldItem.getAmount() - 1);
            player.getInventory().setItemInMainHand(heldItem);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        }

        player.sendMessage("§a§l[Brewery] ✔ Core unit successfully sanitized! The chemical balance has been restored.");
        updateExternalSigns(plugin, clicked);
    }
}