package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption.PlayerIntoxicationMetadata;
import me.arcanebrewery.arcaneBrewery.application.consumption.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConsumptionListener implements Listener {
    private final JavaPlugin plugin;
    private final FinalDrinkMetadata drinks;
    private final PlayerIntoxicationMetadata players;
    private final RecoveryLifecycle lifecycle = new RecoveryLifecycle();

    public ConsumptionListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.drinks = new FinalDrinkMetadata(plugin);
        this.players = new PlayerIntoxicationMetadata(plugin);
    }

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) return;

        var profile = drinks.readConsumption(meta);
        if (profile.isEmpty()) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        RecoveryCondition next = lifecycle.afterDrink(players.read(player), profile.get(), now);
        players.write(player, next);
        applyStatus(player, next, true);

        player.sendMessage("§6[Brewery] Intoxication: §f" + Math.round(next.state().intoxication())
                + "/100 §7| Poisoning: §f" + Math.round(next.state().poisoning()) + "/100");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer(), true);
    }

    public RecoveryCondition refresh(Player player, boolean announce) {
        RecoveryCondition recovered = lifecycle.recover(players.read(player), System.currentTimeMillis());
        players.write(player, recovered);
        applyStatus(player, recovered, announce);
        return recovered;
    }

    public RecoveryLifecycle.Status status(RecoveryCondition condition) {
        return lifecycle.status(condition);
    }

    private void applyStatus(Player player, RecoveryCondition condition, boolean announce) {
        RecoveryLifecycle.Status status = lifecycle.status(condition);
        int shortDuration = 12 * 20;

        switch (status) {
            case CLEAR -> { }
            case BUZZED -> player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 6 * 20, 0, false, false, true));
            case DRUNK -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, shortDuration, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 0, false, false, true));
            }
            case HANGOVER -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, shortDuration, 0, false, false, true));
            }
            case SEVERE_HANGOVER -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, shortDuration, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, shortDuration, 0, false, false, true));
            }
            case WITHDRAWAL -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, shortDuration, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 6 * 20, 0, false, false, true));
            }
            case SEVERE_WITHDRAWAL -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, shortDuration, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, shortDuration, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 0, false, false, true));
            }
        }

        if (condition.state().poisoning() >= 35) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 6 * 20, 0, false, false, true));
        }

        if (!announce) return;
        if (condition.hangover() >= 25) {
            player.sendMessage("§7[Brewery] Hangover symptoms are active.");
        }
        if (condition.withdrawal() >= 25) {
            player.sendMessage("§c[Brewery] Withdrawal symptoms are active.");
        }
    }
}
