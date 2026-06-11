package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

public class AgricultureListener implements Listener {

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public AgricultureListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCropHarvest(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.WHEAT) return;

        if (event.getBlock().getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;

            Player player = event.getPlayer();

            if (random.nextDouble() <= 0.02) {
                ItemStack specialGrain = new ItemStack(Material.WHEAT, 1);
                ItemMeta meta = specialGrain.getItemMeta();

                if (meta != null) {
                    meta.setDisplayName("§b§lExclusive Brewer's Grain");
                    meta.setLore(List.of(
                            "§7A heavy, shimmering grain highly valued by brewmasters.",
                            "§7Required to ferment §a4-Star §7to §e5-Star §7beverages.",
                            "§dHas a volatile 1% chance to yield legendary 6-Star Mythic alcohol!"
                    ));

                    NamespacedKey key = new NamespacedKey(plugin, "premium_grain_tag");
                    meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "TRUE");

                    specialGrain.setItemMeta(meta);

                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), specialGrain);
                    player.sendMessage("§d§l[Brewery] §7You discovered a rare §b§lExclusive Brewer's Grain§7!");
                }
            }
        }
    }
}