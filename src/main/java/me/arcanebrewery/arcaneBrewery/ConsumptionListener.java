package me.arcanebrewery.arcaneBrewery;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption.PlayerIntoxicationMetadata;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.consumption.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
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
    private final PlayerConditionPolicy policy;

    public ConsumptionListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.drinks = new FinalDrinkMetadata(plugin);
        this.players = new PlayerIntoxicationMetadata(plugin);
        this.policy = new PlayerConditionPolicy(plugin.getConfig().getBoolean("consumption.legacy-alcohol-mechanics",false));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION || !(item.getItemMeta() instanceof PotionMeta meta)) return;

        java.util.Optional<DrinkConsumptionProfile> profile;
        java.util.Optional<AlchemyResult> reaction;
        java.util.Optional<EffectProfile> effects;
        try {
            String legacyStage=meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin,"brew_stage"),org.bukkit.persistence.PersistentDataType.STRING);
            if ("INDUSTRIAL_METHANOL".equals(legacyStage)) {
                event.setCancelled(true); event.getPlayer().sendMessage(MessageConfig.prefix()+"§eCleansing Solution is a machine input, not an elixir.§r"); return;
            }
            profile=drinks.readConsumption(meta);
            if (profile.isEmpty()) {
                if (meta.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin,"drink_format"))) event.setCancelled(true);
                return;
            }
            reaction=drinks.readAlchemy(meta);
            effects=drinks.readEffects(meta);
            int format=meta.getPersistentDataContainer().getOrDefault(new org.bukkit.NamespacedKey(plugin,"drink_format"),org.bukkit.persistence.PersistentDataType.INTEGER,0);
            if (format>=3 && reaction.isEmpty()) throw new IllegalArgumentException("incomplete alchemy metadata");
        } catch (RuntimeException corrupt) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MessageConfig.prefix()+"§cThis elixir's metadata is invalid; the bottle was retained.§r"); return;
        }

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        RecoveryCondition next = policy.consume(read(player), profile.get(), now);
        write(player, next);
        if(effects.isEmpty()) applyStatus(player, next, true);
        if(effects.isPresent()) {
            var pending=new java.util.ArrayList<PotionEffect>();
            applyStatus(player,next,true,pending::add);
            if(drinks.prematurePoison(meta)) {
                pending.add(new PotionEffect(PotionEffectType.POISON,10*20,0,false,true,true));
                player.sendMessage(MessageConfig.prefix()+"§cThis prematurely withdrawn batch is poisonous.§r");
            } else {
                me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption.EffectProfileExecution.apply(player,effects.get(),()->safeForwardTeleport(player),pending::add);
                if(effects.get().safety()==EffectProfile.Safety.DANGEROUS)player.sendMessage(MessageConfig.prefix()+"§cThis is a dangerous reaction; inspect its recorded profile.§r");
            }
            me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption.EffectProfileExecution.coalesce(pending).forEach(player::addPotionEffect);
        } else if(drinks.prematurePoison(meta)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON,10*20,0,false,true,true));
            player.sendMessage(MessageConfig.prefix()+"§cThis prematurely withdrawn batch is poisonous.§r");
        } else reaction.ifPresent(alchemy -> applyAlchemy(player, alchemy));

        player.sendMessage(MessageConfig.prefix() + (policy.legacyEnabled() ? "§7Intoxication: §f" : "§7Arcane Saturation: §b")
                + Math.round(next.state().intoxication()) + "/100 §7| Contamination Hazard: §f"
                + Math.round(next.state().poisoning()) + "/100§r");
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
            player.sendMessage("§4§l[ArcaneBrewery] Unstable magical backlash!");
        } else {
            player.sendMessage("§d[ArcaneBrewery] " + PlayerConditionPolicy.readable(alchemy.effect().name()) + " §7("
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
        player.sendMessage("§5[ArcaneBrewery] The teleportation reaction collapses without a safe destination.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer(), true);
    }

    public RecoveryCondition refresh(Player player, boolean announce) {
        RecoveryCondition recovered = policy.recover(read(player), System.currentTimeMillis());
        write(player, recovered);
        applyStatus(player, recovered, announce);
        return recovered;
    }

    public String status(RecoveryCondition condition) {
        return policy.displayStatus(condition);
    }
    public boolean legacyEnabled() { return policy.legacyEnabled(); }
    private RecoveryCondition read(Player player) { return policy.legacyEnabled() ? players.read(player) : players.readFantasy(player); }
    private void write(Player player, RecoveryCondition condition) {
        if (policy.legacyEnabled()) players.write(player,condition); else players.writeFantasy(player,condition);
    }

    private void applyStatus(Player player, RecoveryCondition condition, boolean announce) {applyStatus(player,condition,announce,player::addPotionEffect);}
    private void applyStatus(Player player,RecoveryCondition condition,boolean announce,java.util.function.Consumer<PotionEffect> sink) {
        if (!policy.legacyEnabled()) {
            // Never remove effects: the same types may belong to another plugin
            // or to a legitimate elixir reaction. Our short symptoms expire.
            if (condition.state().intoxication() >= 70) {
                sink.accept(new PotionEffect(PotionEffectType.SLOWNESS, 8 * 20, 0,false,false,true));
                sink.accept(new PotionEffect(PotionEffectType.WEAKNESS, 8 * 20, 0,false,false,true));
            } else if (condition.hangover() >= 25) {
                sink.accept(new PotionEffect(PotionEffectType.WEAKNESS, 8 * 20, 0,false,false,true));
            }

            return;
        }
        RecoveryLifecycle.Status status = new RecoveryLifecycle().status(condition);
        int shortDuration = 12 * 20;

        switch (status) {
            case CLEAR -> { }
            case BUZZED -> sink.accept(new PotionEffect(PotionEffectType.NAUSEA, 6 * 20, 0, false, false, true));
            case DRUNK -> {
                sink.accept(new PotionEffect(PotionEffectType.NAUSEA, shortDuration, 0, false, false, true));
                sink.accept(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 0, false, false, true));
            }
            case HANGOVER -> {
                sink.accept(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 0, false, false, true));
                sink.accept(new PotionEffect(PotionEffectType.MINING_FATIGUE, shortDuration, 0, false, false, true));
            }
            case SEVERE_HANGOVER -> {
                sink.accept(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 1, false, false, true));
                sink.accept(new PotionEffect(PotionEffectType.MINING_FATIGUE, shortDuration, 1, false, false, true));
                sink.accept(new PotionEffect(PotionEffectType.NAUSEA, shortDuration, 0, false, false, true));
            }
            case WITHDRAWAL -> {
                sink.accept(new PotionEffect(PotionEffectType.WEAKNESS, shortDuration, 0, false, false, true));
                sink.accept(new PotionEffect(PotionEffectType.NAUSEA, 6 * 20, 0, false, false, true));
            }
            case SEVERE_WITHDRAWAL -> {
                sink.accept(new PotionEffect(PotionEffectType.WEAKNESS, shortDuration, 1, false, false, true));
                sink.accept(new PotionEffect(PotionEffectType.NAUSEA, shortDuration, 1, false, false, true));
                sink.accept(new PotionEffect(PotionEffectType.SLOWNESS, shortDuration, 0, false, false, true));
            }
        }



        if (!announce) return;
        if (condition.hangover() >= 25) {
            player.sendMessage("§7[ArcaneBrewery] Hangover symptoms are active.");
        }
        if (condition.withdrawal() >= 25) {
            player.sendMessage("§c[ArcaneBrewery] Withdrawal symptoms are active.");
        }
    }
}
