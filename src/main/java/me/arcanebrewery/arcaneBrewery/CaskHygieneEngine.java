package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
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

            if (elapsedSeconds >= 600) {
                tileState.getPersistentDataContainer().set(contKey, PersistentDataType.INTEGER, 1);
                tileState.update();
                return true;
            }
        }
        return false;
    }

    // NEW ADDON METHOD: Scans the entire cross structure layout and pushes real-time text to any linked stats signs!
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

        // Loop a 3x3x3 bubble environment looking for diagnostic signs attached to the core setup
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block checkBlock = barrelBlock.getRelative(x, y, z);
                    if (checkBlock.getState() instanceof Sign sign) {
                        String firstLine = sign.getLine(0);

                        // Verify if this is an active status board sign
                        if (firstLine.contains("[Status]") || firstLine.contains("[Stats]") || firstLine.contains("☣")) {
                            if (isInfected) {
                                sign.setLine(0, "§4§l☣ INFECTED ☣");
                                sign.setLine(1, "§cSTATUS: TOXIC");
                                sign.setLine(2, "§7Needs 4★+");
                                sign.setLine(3, "§7Sanitizer!");
                            } else if (spoilStart != -1L) {
                                sign.setLine(0, "§4§l[CRITICAL]");
                                sign.setLine(1, "§cMOLD EXPIRY:");
                                sign.setLine(2, "§f" + Math.max(0, remainSeconds) + "s");
                                sign.setLine(3, "§7Extract Slop!");
                            } else {
                                sign.setLine(0, "§2§l[Cask Diagnostics]");
                                sign.setLine(1, "§aSTATUS: PURE");
                                sign.setLine(2, "§7Atmosphere OK");
                                sign.setLine(3, "§7✔ READY");
                            }
                            sign.update();
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCaskSanitize(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.BARREL) return;

        Player player = event.getPlayer();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem.getType() != Material.POTION || !heldItem.hasItemMeta()) return;

        TileState tileState = (TileState) clicked.getState();
        if (tileState.getPersistentDataContainer().getOrDefault(contaminationKey, PersistentDataType.INTEGER, 0) == 0) {
            return;
        }

        PotionMeta meta = (PotionMeta) heldItem.getItemMeta();
        if (!meta.getPersistentDataContainer().has(starRatingKey, PersistentDataType.INTEGER)) return;

        int qualityStars = meta.getPersistentDataContainer().getOrDefault(starRatingKey, PersistentDataType.INTEGER, 0);

        if (qualityStars < 4) {
            player.sendMessage("§c[Brewery] This alcohol proof is too low! You need a 4★+ high-purity brew to dissolve the contamination.");
            return;
        }

        event.setCancelled(true);

        tileState.getPersistentDataContainer().set(contaminationKey, PersistentDataType.INTEGER, 0);
        tileState.getPersistentDataContainer().remove(spoilTimeKey);
        tileState.update();

        clicked.getWorld().playSound(clicked.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.2f, 0.8f);
        clicked.getWorld().playSound(clicked.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 1.0f, 1.1f);
        clicked.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, clicked.getLocation().add(0.5, 1.1, 0.5), 25, 0.3, 0.3, 0.3, 0.05);

        if (heldItem.getAmount() > 1) {
            heldItem.setAmount(heldItem.getAmount() - 1);
            player.getInventory().setItemInMainHand(heldItem);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        }

        player.sendMessage("§a§l[Brewery] ✔ Cask interior successfully sanitized! The bacteria has been cleared.");

        // Push an immediate text reset out to the signs upon successful sanitization!
        updateExternalSigns(plugin, clicked);
    }
}