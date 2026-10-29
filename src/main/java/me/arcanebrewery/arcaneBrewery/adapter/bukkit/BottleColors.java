package me.arcanebrewery.arcaneBrewery.adapter.bukkit;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.application.alchemy.ProfileEffect;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

/** Cosmetic liquid tint only. Never adds native effects or rewrites stored brewing statistics. */
public final class BottleColors {
    private BottleColors() { }
    public static Color color(ProfileEffect effect) {
        return Color.fromRGB(switch (effect) {
            case SPEED -> 0x7CAFC6;
            case HASTE -> 0xD9C043;
            case STRENGTH -> 0x932423;
            case JUMP_BOOST -> 0x22FF4C;
            case FIRE_RESISTANCE -> 0xE49A3A;
            case REGENERATION -> 0xCD5CAB;
            case NIGHT_VISION -> 0x5B56C4;
            case WATER_BREATHING, DOLPHINS_GRACE -> 0x2E82CE;
            case INVISIBILITY -> 0xA9B8C2;
            case SLOW_FALLING -> 0xF3E9C5;
            case RESISTANCE -> 0x99453A;
            case ABSORPTION -> 0xEBC84C;
            case LEVITATION -> 0xCEFFFF;
            case TELEPORTATION -> 0x863AB8;
            case HALLUCINATION -> 0xD45FE0;
            case CURSE, WITHER -> 0x51445F;
            case HUNGER -> 0x587653;
            case SLOWNESS -> 0x5A6C81;
            case WEAKNESS -> 0x777777;
            case NONE -> 0xDAA548;
        });
    }
    public static void apply(ItemMeta item) {
        if (!(item instanceof PotionMeta meta)) return;
        var data = new FinalDrinkMetadata("arcanebrewery");
        if (data.readConsumption(meta).isPresent()) {
            var effect = data.readEffects(meta).flatMap(p -> p.effects().stream().filter(e -> !e.sideEffect()).findFirst())
                .map(e -> e.effect()).orElseGet(() -> data.readAlchemy(meta)
                    .map(a -> ProfileEffect.valueOf(a.effect().name())).orElse(ProfileEffect.NONE));
            meta.setColor(color(effect));
        } else {
            var pdc = meta.getPersistentDataContainer();
            if (pdc.has(new NamespacedKey("arcanebrewery", "fermentation_format"), PersistentDataType.INTEGER))
                meta.setColor(Color.fromRGB(0xB981C9));
            else if (pdc.has(new NamespacedKey("arcanebrewery", "distillery_format"), PersistentDataType.INTEGER))
                meta.setColor(Color.fromRGB(0xE6BD63));
        }
    }
}
