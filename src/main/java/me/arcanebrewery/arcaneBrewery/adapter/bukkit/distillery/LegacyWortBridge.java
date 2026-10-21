package me.arcanebrewery.arcaneBrewery.adapter.bukkit.distillery;

import me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryProcessDraft;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;

import java.util.Comparator;

public final class LegacyWortBridge {
    public String stageFor(DistilleryProcessDraft draft) {
        String dominant = draft.ingredientRatios().entrySet().stream()
                .max(java.util.Map.Entry.<String, Double>comparingByValue()
                        .thenComparing(java.util.Map.Entry::getKey, Comparator.reverseOrder()))
                .orElseThrow().getKey();
        if (BuiltInIngredients.PREMIUM_GRAIN.equals(dominant)) return "PREMIUM_WORT_BEER";
        return switch (dominant) {
            case "minecraft:wheat" -> "WORT_BEER";
            case "minecraft:sugar_cane" -> "WORT_RUM";
            case "minecraft:sugar" -> "WORT_VODKA";
            case "minecraft:apple" -> "WORT_CIDER";
            case "minecraft:potato" -> "WORT_VODKA";
            case "minecraft:sweet_berries" -> "WORT_WINE";
            case "minecraft:honeycomb" -> "WORT_MEAD";
            case "minecraft:kelp" -> "WORT_PIT_BEER";
            case "minecraft:melon_slice" -> "WORT_TONIC";
            case "minecraft:cactus" -> "WORT_CACTUS";
            case "minecraft:brown_mushroom", "minecraft:red_mushroom" -> "WORT_MOONSHINE";
            case "minecraft:cocoa_beans" -> "WORT_TODDY";
            case "minecraft:blaze_powder" -> "WORT_WHISKEY";
            case "minecraft:chorus_fruit" -> "WORT_BLINK";
            case "minecraft:glistering_melon_slice" -> "WORT_GOLD";
            case "minecraft:glowstone_dust" -> "WORT_BRANDY";
            case "minecraft:wither_skeleton_skull" -> "WORT_WITHER";
            case "minecraft:prismarine_crystals" -> "WORT_RUM";
            case "minecraft:amethyst_shard" -> "WORT_AMETHYST";
            case "minecraft:netherite_scrap" -> "WORT_DEBRIS";
            case "minecraft:echo_shard" -> "WORT_WARDEN";
            case "minecraft:shulker_shell" -> "WORT_SHULKER";
            default -> "WORT_EXPERIMENTAL";
        };
    }
}
