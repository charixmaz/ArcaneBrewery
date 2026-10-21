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
        this(plugin.getName().toLowerCase(Locale.ROOT));
    }

    public PlayerIntoxicationMetadata(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    public RecoveryCondition read(Player player) {
        var pdc = player.getPersistentDataContainer();
        double intox = pdc.getOrDefault(key("player_intoxication"), PersistentDataType.DOUBLE, 0.0);
        double tolerance = pdc.getOrDefault(key("player_tolerance"), PersistentDataType.DOUBLE, 0.0);
        double poisoning = pdc.getOrDefault(key("player_poisoning"), PersistentDataType.DOUBLE, 0.0);
        double dependence = pdc.getOrDefault(key("player_dependence"), PersistentDataType.DOUBLE, 0.0);
        long updated = pdc.getOrDefault(key("player_intox_updated"), PersistentDataType.LONG, 0L);
        double hangover = pdc.getOrDefault(key("player_hangover"), PersistentDataType.DOUBLE, 0.0);
        double withdrawal = pdc.getOrDefault(key("player_withdrawal"), PersistentDataType.DOUBLE, 0.0);
        return new RecoveryCondition(
                new IntoxicationState(intox, tolerance, poisoning, dependence, updated),
                hangover, withdrawal, updated);
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
}
