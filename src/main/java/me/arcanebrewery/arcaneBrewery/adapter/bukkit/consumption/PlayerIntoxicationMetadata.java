package me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption;

import me.arcanebrewery.arcaneBrewery.application.consumption.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

public final class PlayerIntoxicationMetadata {
    public static final int VERSION = 2;
    private final String namespace;

    public PlayerIntoxicationMetadata(JavaPlugin plugin) {
        this("arcanebrewery");
    }

    public PlayerIntoxicationMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public RecoveryCondition read(Player player) {
        var pdc = player.getPersistentDataContainer();
        double intox = number(player,"player_intoxication");
        double tolerance = number(player,"player_tolerance");
        double poisoning = number(player,"player_poisoning");
        double dependence = number(player,"player_dependence");
        long updated = timestamp(player,"player_intox_updated");
        double hangover = number(player,"player_hangover");
        double withdrawal = number(player,"player_withdrawal");
        return new RecoveryCondition(
                new IntoxicationState(intox, tolerance, poisoning, dependence, updated),
                hangover, withdrawal, updated);
    }

    public RecoveryCondition readFantasy(Player player) {
        var data = player.getPersistentDataContainer();
        if (!data.has(key("player_arcane_updated"))) return PlayerConditionPolicy.importLegacy(read(player));
        double saturation = number(player,"player_arcane_saturation");
        double hazard = number(player,"player_arcane_contamination");
        double recovery = number(player,"player_arcane_recovery");
        long updated = timestamp(player,"player_arcane_updated");
        return new RecoveryCondition(new IntoxicationState(saturation,0,hazard,0,updated),recovery,0,updated);
    }

    public void writeFantasy(Player player, RecoveryCondition condition) {
        var data = player.getPersistentDataContainer();
        data.set(key("player_arcane_saturation"), PersistentDataType.DOUBLE, condition.state().intoxication());
        data.set(key("player_arcane_contamination"), PersistentDataType.DOUBLE, condition.state().poisoning());
        data.set(key("player_arcane_recovery"), PersistentDataType.DOUBLE, condition.hangover());
        data.set(key("player_arcane_updated"), PersistentDataType.LONG, condition.updatedAtEpochMillis());
    }

    public void write(Player player, RecoveryCondition condition) {
        IntoxicationState state = condition.state();
        var pdc = player.getPersistentDataContainer();
        pdc.set(key("player_consumption_format"), PersistentDataType.INTEGER, VERSION);
        pdc.set(key("player_intoxication"), PersistentDataType.DOUBLE, state.intoxication());
        pdc.set(key("player_tolerance"), PersistentDataType.DOUBLE, state.tolerance());
        pdc.set(key("player_poisoning"), PersistentDataType.DOUBLE, state.poisoning());
        pdc.set(key("player_dependence"), PersistentDataType.DOUBLE, state.dependence());
        pdc.set(key("player_hangover"), PersistentDataType.DOUBLE, condition.hangover());
        pdc.set(key("player_withdrawal"), PersistentDataType.DOUBLE, condition.withdrawal());
        pdc.set(key("player_intox_updated"), PersistentDataType.LONG, condition.updatedAtEpochMillis());
    }
    private double number(Player player,String name) {
        try {
            double value=player.getPersistentDataContainer().getOrDefault(key(name),PersistentDataType.DOUBLE,0.0);
            return Double.isFinite(value)?Math.max(0,Math.min(100,value)):0;
        } catch (RuntimeException malformed) { return 0; }
    }
    private long timestamp(Player player,String name) {
        try { return Math.max(0,player.getPersistentDataContainer().getOrDefault(key(name),PersistentDataType.LONG,0L)); }
        catch (RuntimeException malformed) { return 0; }
    }

}
