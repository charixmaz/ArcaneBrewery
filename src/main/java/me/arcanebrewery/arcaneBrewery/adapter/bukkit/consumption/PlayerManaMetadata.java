package me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption;

import me.arcanebrewery.arcaneBrewery.application.consumption.*;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Objects;

public final class PlayerManaMetadata {
    public static final int VERSION = 2;
    private final String namespace;

    public PlayerManaMetadata(JavaPlugin plugin) {
        this("arcanebrewery");
    }

    public PlayerManaMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public RecoveryCondition read(Player player) {
        var data=player.getPersistentDataContainer();
        if (!data.has(key("player_arcane_updated"))) {
            long updated=timestamp(player,"player_intox_updated");
            return new RecoveryCondition(new ManaState(number(player,"player_intoxication"),number(player,"player_poisoning"),updated),0,updated);
        }
        long updated=timestamp(player,"player_arcane_updated");
        return new RecoveryCondition(new ManaState(number(player,"player_arcane_saturation"),number(player,"player_arcane_contamination"),updated),number(player,"player_arcane_recovery"),updated);
    }
    public void write(Player player,RecoveryCondition condition) {
        var data=player.getPersistentDataContainer();
        data.set(key("player_arcane_saturation"),PersistentDataType.DOUBLE,condition.state().load());
        data.set(key("player_arcane_contamination"),PersistentDataType.DOUBLE,condition.state().hazard());
        data.set(key("player_arcane_recovery"),PersistentDataType.DOUBLE,condition.depletion());
        data.set(key("player_arcane_updated"),PersistentDataType.LONG,condition.updatedAtEpochMillis());
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
