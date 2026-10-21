package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.Map;
import java.util.Optional;

public final class BuiltInAlchemyCatalysts {
    private static final Map<String, AlchemyCatalyst> BY_EXTERNAL = Map.ofEntries(
            Map.entry("minecraft:blaze_powder",
                    new AlchemyCatalyst("arcanebrewery:fire_catalyst", "arcanebrewery:fire_catalyst", 1.0, "Ember Catalyst")),
            Map.entry("minecraft:amethyst_shard",
                    new AlchemyCatalyst("arcanebrewery:invisibility_catalyst", "arcanebrewery:invisibility_catalyst", 1.0, "Veil Catalyst")),
            Map.entry("minecraft:glistering_melon_slice",
                    new AlchemyCatalyst("arcanebrewery:healing_catalyst", "arcanebrewery:healing_catalyst", 1.0, "Vital Catalyst")),
            Map.entry("minecraft:glowstone_dust",
                    new AlchemyCatalyst("arcanebrewery:vision_catalyst", "arcanebrewery:vision_catalyst", 1.0, "Radiant Catalyst")),
            Map.entry("minecraft:shulker_shell",
                    new AlchemyCatalyst("arcanebrewery:levitation_catalyst", "arcanebrewery:levitation_catalyst", 1.0, "Aerial Catalyst")),
            Map.entry("minecraft:chorus_fruit",
                    new AlchemyCatalyst("arcanebrewery:teleport_catalyst", "arcanebrewery:teleport_catalyst", 1.0, "Phase Catalyst")),
            Map.entry("minecraft:netherite_scrap",
                    new AlchemyCatalyst("arcanebrewery:curse_catalyst", "arcanebrewery:curse_catalyst", 1.0, "Hex Catalyst")),
            Map.entry("minecraft:wither_skeleton_skull",
                    new AlchemyCatalyst("arcanebrewery:wither_catalyst", "arcanebrewery:wither_catalyst", 1.0, "Decay Catalyst"))
    );

    private BuiltInAlchemyCatalysts() {}

    public static Optional<AlchemyCatalyst> resolveExternal(String externalId) {
        return Optional.ofNullable(BY_EXTERNAL.get(externalId));
    }

    public static Map<String, AlchemyCatalyst> all() {
        return BY_EXTERNAL;
    }
}
