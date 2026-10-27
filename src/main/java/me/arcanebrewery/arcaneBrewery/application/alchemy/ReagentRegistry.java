package me.arcanebrewery.arcaneBrewery.application.alchemy;
import java.util.*;
import static me.arcanebrewery.arcaneBrewery.application.alchemy.ProfileEffect.*;
import static me.arcanebrewery.arcaneBrewery.application.alchemy.ReagentRole.*;
/** Explicit data, no identifier substring heuristics. Existing eight IDs/doses retain their meaning. */
public final class ReagentRegistry {
    private ReagentRegistry() { }
    private static ReagentDefinition effect(String material,String id,String name,int priority,int seconds,boolean risky,ProfileEffect... effects) {
        return new ReagentDefinition("minecraft:"+material,new AlchemyCatalyst("arcanebrewery:"+id,"arcanebrewery:"+id,1,name),name,EFFECT_CATALYST,List.of(effects),priority,seconds,risky);
    }
    private static ReagentDefinition modifier(String material,String id,String name,ReagentRole role) {
        double[] properties=switch(material) {
            case "redstone" -> new double[]{1.5,.75,0};case "redstone_block" -> new double[]{1.8,.75,0};
            case "lapis_lazuli" -> new double[]{1.25,.9,5};case "glowstone" -> new double[]{.65,1.6,-8};
            case "amethyst_block" -> new double[]{1,1,18};case "quartz" -> new double[]{1,.8,10};
            default -> throw new IllegalArgumentException("missing modifier properties");
        };
        return new ReagentDefinition("minecraft:"+material,new AlchemyCatalyst("arcanebrewery:"+id,"arcanebrewery:"+id,1,name),name,role,List.of(),0,0,false,properties[0],properties[1],properties[2]);
    }
    private static final List<ReagentDefinition> DEFINITIONS=List.of(
        effect("blaze_powder","fire_catalyst","Blaze Powder",107,40,false,FIRE_RESISTANCE),
        effect("amethyst_shard","invisibility_catalyst","Amethyst Shard",105,30,false,INVISIBILITY),
        effect("glistering_melon_slice","healing_catalyst","Glistering Melon",106,12,false,REGENERATION),
        effect("glowstone_dust","vision_catalyst","Glowstone Dust",102,45,false,NIGHT_VISION),
        effect("shulker_shell","levitation_catalyst","Shulker Shell",104,6,true,LEVITATION),
        effect("chorus_fruit","teleport_catalyst","Chorus Fruit",103,1,false,TELEPORTATION),
        effect("netherite_scrap","curse_catalyst","Netherite Scrap",108,12,true,CURSE),
        effect("wither_skeleton_skull","wither_catalyst","Wither Skeleton Skull",101,5,true,WITHER),
        effect("blaze_rod","combat_catalyst","Blaze Rod",90,25,false,STRENGTH),
        effect("iron_nugget","mining_catalyst","Iron Nugget",85,35,false,HASTE),
        effect("sugar","travel_catalyst","Sugar",75,40,false,SPEED),
        effect("prismarine_crystals","aquatic_catalyst","Prismarine Crystals",65,45,false,WATER_BREATHING),
        effect("nautilus_shell","current_catalyst","Nautilus Shell",64,20,false,DOLPHINS_GRACE),
        effect("ghast_tear","restoration_catalyst","Ghast Tear",62,20,false,REGENERATION),
        effect("rabbit_foot","leaping_catalyst","Rabbit Foot",55,35,false,JUMP_BOOST),
        effect("phantom_membrane","falling_catalyst","Phantom Membrane",54,30,false,SLOW_FALLING),
        effect("turtle_scute","ward_catalyst","Turtle Scute",53,20,false,RESISTANCE),
        effect("golden_apple","vigor_catalyst","Golden Apple",52,30,false,ABSORPTION),
        modifier("redstone","duration_reagent","Redstone Dust",DURATION_REAGENT),
        modifier("redstone_block","dense_duration_reagent","Redstone Block",DURATION_REAGENT),
        modifier("glowstone","potency_reagent","Glowstone Block",POTENCY_REAGENT),
        modifier("amethyst_block","stability_reagent","Amethyst Block",STABILIZER),
        modifier("quartz","clarity_reagent","Nether Quartz",STABILIZER),
        modifier("lapis_lazuli","measured_duration_reagent","Lapis Lazuli",DURATION_REAGENT),
        effect("echo_shard","echo_catalyst","Echo Shard",15,8,true,HALLUCINATION),
        effect("dragon_breath","dragon_catalyst","Dragon Breath",95,15,true,STRENGTH,RESISTANCE)
    );
    public static List<ReagentDefinition> all() {return DEFINITIONS;}
    public static Optional<ReagentDefinition> external(String id) {return DEFINITIONS.stream().filter(d->d.externalId().equals(id)).findFirst();}
    public static Optional<ReagentDefinition> additive(String id) {return DEFINITIONS.stream().filter(d->d.additiveId().equals(id)).findFirst();}
    public static Optional<ReagentDefinition> stableId(String id) {return DEFINITIONS.stream().filter(d->d.id().equals(id)).findFirst();}
    public static void validateCombination(Map<String,Double> additives) {
        var effects=EnumSet.noneOf(ProfileEffect.class);boolean modifier=false;
        for(var e:additives.entrySet()) {
            var d=additive(e.getKey()).orElse(null);if(d==null) continue; // legacy custom definitions remain decodable
            if(d.role()==EFFECT_CATALYST) effects.addAll(d.effects());else modifier=true;
        }
        if(effects.isEmpty() && modifier) throw new IllegalArgumentException("A modifier needs an effect catalyst in the other slot");
        if(effects.contains(SPEED) && effects.contains(STRENGTH)) throw new IllegalArgumentException("Travel and combat catalysts conflict; choose a compatible family");
        if(effects.contains(LEVITATION) && (effects.contains(SLOW_FALLING)||effects.contains(JUMP_BOOST))) throw new IllegalArgumentException("Levitation conflicts with falling/leaping catalysts");
        if((effects.contains(CURSE)||effects.contains(WITHER)||effects.contains(HALLUCINATION)) && effects.size()>1) throw new IllegalArgumentException("Dangerous transformations cannot be combined with utility effects");
    }
}
