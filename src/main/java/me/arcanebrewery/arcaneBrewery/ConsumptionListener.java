package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption.PlayerIntoxicationMetadata;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
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
        drinks.readAlchemy(meta).ifPresent(alchemy -> applyAlchemy(player, alchemy));

        player.sendMessage("§6[Brewery] Intoxication: §f" + Math.round(next.state().intoxication())
                + "/100 §7| Poisoning: §f" + Math.round(next.state().poisoning()) + "/100");
    }

    private void applyAlchemy(Player player, AlchemyResult alchemy) {
        if (alchemy.effect() == AlchemyEffect.NONE) return;

        int amplifier = alchemy.potency() >= 75 ? 1 : 0;
        int duration = (int) Math.max(5 * 20, Math.min(60 * 20, (10 + alchemy.potency() * 0.5) * 20));

        switch (alchemy.effect()) {
            case NONE -> { }
            case FIRE_RESISTANCE -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.FIRE_RESISTANCE, duration, amplifier, false, true, true));
            case INVISIBILITY -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, duration, 0, false, true, true));
            case REGENERATION -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION, Math.min(duration, 30 * 20), amplifier, false, true, true));
            case NIGHT_VISION -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.NIGHT_VISION, duration, 0, false, true, true));
            case LEVITATION -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.LEVITATION, Math.min(duration, 12 * 20), 0, false, true, true));
            case TELEPORTATION -> safeForwardTeleport(player);
            case HALLUCINATION -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NAUSEA, Math.min(duration, 25 * 20), amplifier, false, true, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.DARKNESS, Math.min(duration, 18 * 20), 0, false, true, true));
            }
            case CURSE -> {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.WEAKNESS, Math.min(duration, 30 * 20), amplifier, false, true, true));
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, Math.min(duration, 20 * 20), 0, false, true, true));
            }
            case WITHER -> player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WITHER, Math.min(duration, 12 * 20), amplifier, false, true, true));
        }

        if (alchemy.dangerousReaction()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS, 8 * 20, 0, false, true, true));
            player.sendMessage("§4§l[Alchemy] Unstable magical backlash!");
        } else {
            player.sendMessage("§d[Alchemy] " + alchemy.effect() + " §7("
                    + Math.round(alchemy.potency()) + "% potency)");
        }
    }

    private void safeForwardTeleport(Player player) {
        var start = player.getLocation();
        var direction = start.getDirection().normalize();
        for (int distance = 5; distance >= 2; distance--) {
            var target = start.clone().add(direction.clone().multiply(distance));
            var feet = target.getBlock();
            var head = target.clone().add(0, 1, 0).getBlock();
            var floor = target.clone().add(0, -1, 0).getBlock();
            if (feet.isPassable() && head.isPassable() && floor.getType().isSolid()) {
                target.setYaw(start.getYaw());
                target.setPitch(start.getPitch());
                player.teleport(target);
                return;
            }
        }
        player.sendMessage("§5[Alchemy] The teleportation reaction collapses without a safe destination.");
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
