package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import me.arcanebrewery.arcaneBrewery.application.discovery.MaterialKey;
/** Acquisition clues only; no material labels, effect properties or formulations. */
public final class MaterialRiddles {
 private MaterialRiddles() { }
 public static String clue(MaterialKey key){return switch(key.registryId()){
 case "minecraft:wheat" -> "A field of golden heads bows when the harvest is ready.";
 case "minecraft:sugar_cane" -> "Jointed green towers thrive with their feet beside water.";
 case "minecraft:apple" -> "Search the leafy crown after the trunk has fallen.";
 case "minecraft:golden_apple" -> key.kind()==MaterialKey.Kind.INGREDIENT?"An orchard gift wears a costly yellow coat.":"A familiar orchard treasure hides inside eight gleaming offerings.";
 case "minecraft:potato" -> "Dig below a village crop; this supper grows out of sight.";
 case "minecraft:sweet_berries" -> "Cold woodland thorns guard a handful of red sweetness.";
 case "minecraft:honeycomb" -> "Shear a full buzzing home with smoke beneath it.";
 case "arcanebrewery:premium_grain" -> "A patient reaper sometimes finds an exceptional seed among mature golden fields.";
 case "arcanebrewery:fire_catalyst" -> "Reduce a fiery fortress trophy to glowing grains.";
 case "arcanebrewery:invisibility_catalyst" -> "A singing violet cave grows sharp crystalline fragments.";
 case "arcanebrewery:healing_catalyst" -> "A summer slice becomes a shining treasure on the crafting bench.";
 case "arcanebrewery:vision_catalyst" -> "Break the hanging light of the infernal ceiling into sparkling grains.";
 case "arcanebrewery:levitation_catalyst" -> "A box-shaped guardian of distant towers leaves half its armour.";
 case "arcanebrewery:teleport_catalyst" -> "Branching purple trees beyond the void bear strange edible bulbs.";
 case "arcanebrewery:curse_catalyst" -> "Smelt a buried relic from the deepest infernal rubble.";
 case "arcanebrewery:wither_catalyst" -> "A tall fortress swordsman rarely yields this dark trophy.";
 case "arcanebrewery:combat_catalyst" -> "Keep the fiery fortress trophy whole rather than crushing it.";
 case "arcanebrewery:mining_catalyst" -> "Divide a common pale metal ingot into nine tiny pieces.";
 case "arcanebrewery:travel_catalyst" -> "Mill the jointed riverside crop into sweet pale crystals.";
 case "arcanebrewery:aquatic_catalyst" -> "An ocean monument guardian carries a clear piece of its home.";
 case "arcanebrewery:current_catalyst" -> "The drowned sometimes clutch a spiral from the deep.";
 case "arcanebrewery:restoration_catalyst" -> "A floating pale infernal wailer sheds a rare sorrow.";
 case "arcanebrewery:leaping_catalyst" -> "A small hopping animal rarely leaves this lucky keepsake.";
 case "arcanebrewery:falling_catalyst" -> "Sleepless nights summon wings with a leathery remnant.";
 case "arcanebrewery:ward_catalyst" -> "Wait for a beach hatchling to grow and leave its old protection.";
 case "arcanebrewery:vigor_catalyst" -> "Wrap an orchard harvest in eight pieces of precious yellow metal.";
 case "arcanebrewery:duration_reagent" -> "Deep stone veins crumble into a red dust that carries signals.";
 case "arcanebrewery:dense_duration_reagent" -> "Nine portions of the deep red signal become one solid cube.";
 case "arcanebrewery:potency_reagent" -> "Reassemble four grains of infernal ceiling light into a luminous cube.";
 case "arcanebrewery:stability_reagent" -> "A violet geode holds solid singing walls as well as sharp growths.";
 case "arcanebrewery:clarity_reagent" -> "Infernal stone conceals pale mineral seams.";
 case "arcanebrewery:measured_duration_reagent" -> "Blue underground flecks once supplied a painter's deepest colour.";
 case "arcanebrewery:echo_catalyst" -> "A silent ancient city guards a fragment that remembers sound.";
 case "arcanebrewery:dragon_catalyst" -> "Catch a final guardian's lingering breath in an empty vessel.";
 case "arcanebrewery:prepared_duration" -> "A researched workshop presses deep red grains with a violet fragment.";
 case "arcanebrewery:prepared_potency" -> "A researched workshop binds luminous grains to an unbroken fiery trophy.";
 case "arcanebrewery:prepared_purification" -> "A researched workshop brings pale infernal mineral and a buzzing harvest together.";
 default -> "Explore and obtain this registered material to learn its use.";
 };}
}
