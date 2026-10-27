package me.arcanebrewery.arcaneBrewery.application.alchemy;
import java.util.*;
/** Short innate effects for new uninfused batches; never an affinity-based hallucination fallback. */
public final class NaturalEffectRegistry {
    private NaturalEffectRegistry() { }
    public record Definition(ProfileEffect effect,int seconds,int priority,boolean risky) { }
    private static final Map<String,Definition> VALUES=Map.ofEntries(
        Map.entry("minecraft:apple",new Definition(ProfileEffect.REGENERATION,6,20,false)),
        Map.entry("minecraft:golden_apple",new Definition(ProfileEffect.ABSORPTION,12,30,false)),
        Map.entry("minecraft:honeycomb",new Definition(ProfileEffect.REGENERATION,8,25,false)),
        Map.entry("minecraft:sugar_cane",new Definition(ProfileEffect.SPEED,15,15,false)),
        Map.entry("minecraft:sweet_berries",new Definition(ProfileEffect.NIGHT_VISION,15,10,false)),
        Map.entry("minecraft:potato",new Definition(ProfileEffect.HASTE,8,12,false)),
        Map.entry("arcanebrewery:premium_grain",new Definition(ProfileEffect.HASTE,12,18,false)),
        Map.entry("minecraft:blaze_powder",new Definition(ProfileEffect.FIRE_RESISTANCE,20,40,false)),
        Map.entry("minecraft:chorus_fruit",new Definition(ProfileEffect.TELEPORTATION,1,41,false)),
        Map.entry("minecraft:glistering_melon_slice",new Definition(ProfileEffect.REGENERATION,6,42,false)),
        Map.entry("minecraft:glowstone_dust",new Definition(ProfileEffect.NIGHT_VISION,20,43,false)),
        Map.entry("minecraft:amethyst_shard",new Definition(ProfileEffect.INVISIBILITY,15,44,false)),
        Map.entry("minecraft:shulker_shell",new Definition(ProfileEffect.LEVITATION,3,45,true)),
        Map.entry("minecraft:echo_shard",new Definition(ProfileEffect.HALLUCINATION,5,46,true)),
        Map.entry("minecraft:wither_skeleton_skull",new Definition(ProfileEffect.WITHER,3,47,true)),
        Map.entry("minecraft:netherite_scrap",new Definition(ProfileEffect.CURSE,6,48,true))
    );
    public static Optional<Definition> ingredient(String id){return Optional.ofNullable(VALUES.get(id));}
}
