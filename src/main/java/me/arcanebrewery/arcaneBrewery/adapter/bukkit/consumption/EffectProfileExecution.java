package me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.*;
import java.util.*;
/** Only called for successful consumption. Never adds native tooltip effects to bottles. */
public final class EffectProfileExecution {
    private EffectProfileExecution() { }
    public static void apply(Player player,EffectProfile profile,Runnable teleport) {
        apply(player,profile,teleport,player::addPotionEffect);
    }
    public static void apply(Player player,EffectProfile profile,Runnable teleport,java.util.function.Consumer<PotionEffect> sink) {
        for(var action:EffectExecutionPlan.from(profile)) {
            if(action.key().equals("TELEPORTATION")){teleport.run();continue;}
            PotionEffectType type=switch(action.key()) {
                case "NAUSEA" -> PotionEffectType.NAUSEA;case "DARKNESS" -> PotionEffectType.DARKNESS;
                default -> type(ProfileEffect.valueOf(action.key()));
            };
            if(type!=null)sink.accept(new PotionEffect(type,action.seconds()*20,action.amplifier(),false,true,true));
        }
    }
    public static List<PotionEffect> coalesce(Collection<PotionEffect> effects) {
        var combined=new LinkedHashMap<PotionEffectType,PotionEffect>();
        for(var e:effects) combined.merge(e.getType(),e,(a,b)->new PotionEffect(a.getType(),Math.max(a.getDuration(),b.getDuration()),Math.max(a.getAmplifier(),b.getAmplifier()),false,true,true));
        return List.copyOf(combined.values());
    }
    private static PotionEffectType type(ProfileEffect effect) {
        return switch(effect) {
            case FIRE_RESISTANCE -> PotionEffectType.FIRE_RESISTANCE;case INVISIBILITY -> PotionEffectType.INVISIBILITY;
            case REGENERATION -> PotionEffectType.REGENERATION;case NIGHT_VISION -> PotionEffectType.NIGHT_VISION;
            case LEVITATION -> PotionEffectType.LEVITATION;case WITHER -> PotionEffectType.WITHER;
            case HASTE -> PotionEffectType.HASTE;case SPEED -> PotionEffectType.SPEED;case STRENGTH -> PotionEffectType.STRENGTH;
            case WATER_BREATHING -> PotionEffectType.WATER_BREATHING;case DOLPHINS_GRACE -> PotionEffectType.DOLPHINS_GRACE;
            case JUMP_BOOST -> PotionEffectType.JUMP_BOOST;case SLOW_FALLING -> PotionEffectType.SLOW_FALLING;
            case RESISTANCE -> PotionEffectType.RESISTANCE;case ABSORPTION -> PotionEffectType.ABSORPTION;
            case HUNGER -> PotionEffectType.HUNGER;case SLOWNESS -> PotionEffectType.SLOWNESS;case WEAKNESS -> PotionEffectType.WEAKNESS;
            case NONE,TELEPORTATION,HALLUCINATION,CURSE -> null;
        };
    }
}
