package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption.PlayerIntoxicationMetadata;
import me.arcanebrewery.arcaneBrewery.application.consumption.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConsumptionListener implements Listener {
    private final JavaPlugin plugin;
    private final FinalDrinkMetadata drinks;
    private final PlayerIntoxicationMetadata players;
    private final IntoxicationModel model = new IntoxicationModel();

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
        IntoxicationState current = players.read(player);
        ConsumptionOutcome outcome = model.consume(current, profile.get(), now);
        players.write(player, outcome.state());
        applyEffects(player, outcome);

        player.sendMessage("§6[Brewery] Intoxication: §f" + Math.round(outcome.state().intoxication())
                + "/100 §7| Poisoning: §f" + Math.round(outcome.state().poisoning()) + "/100");
    }

    private void applyEffects(Player player, ConsumptionOutcome outcome) {
        int duration = 20 * 20;

        switch (outcome.severity()) {
            case SOBER -> { }
            case BUZZED -> player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 8 * 20, 0, false, false, true));
            case DRUNK -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 12 * 20, 0, false, false, true));
            }
            case HEAVILY_DRUNK -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 0, false, false, true));
            }
            case DANGEROUS -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30 * 20, 2, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30 * 20, 2, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30 * 20, 1, false, false, true));
            }
        }

        if (outcome.nausea()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 15 * 20, 0, false, false, true));
        }
        if (outcome.poisoningRisk()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 8 * 20, 0, false, false, true));
            player.sendMessage("§c[Brewery] Toxic compounds are affecting you.");
        }
        if (outcome.hangoverRisk()) {
            player.sendMessage("§7[Brewery] This dose carries a hangover risk.");
        }
    }
}
