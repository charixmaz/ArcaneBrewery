package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.application.ingredient.*;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import java.util.*;

/** Item metadata is authoritative only when explicitly stamped and registered; names/lore are ignored. */
public final class IngredientItemAdapter {
    private final IngredientRegistry registry;
    private final IngredientMetadataCodec metadata;
    private final IngredientPresentation presentation;

    public IngredientItemAdapter(IngredientRegistry registry, IngredientMetadataCodec metadata) {
        this.registry = Objects.requireNonNull(registry);
        this.metadata = Objects.requireNonNull(metadata);
        this.presentation = new IngredientPresentation(metadata);
    }
    public IngredientRegistry registry() { return registry; }
    public IngredientMetadataCodec metadata() { return metadata; }

    public IngredientInput classify(ItemStack item) {
        if (item == null || item.getAmount() <= 0 || item.getType() == Material.AIR
                || item.getType() == Material.CAVE_AIR || item.getType() == Material.VOID_AIR)
            return new IngredientInput.Empty();
        Optional<String> canonical = item.hasItemMeta()
                ? metadata.canonicalId(item.getItemMeta().getPersistentDataContainer()) : Optional.empty();
        if (canonical.isPresent()) return ingredient(item, registry.require(canonical.get()), true);
        if (metadata.legacyPremium(item)) return ingredient(item, registry.require(BuiltInIngredients.PREMIUM_GRAIN), false);
        var mapped = registry.resolveExternal(item.getType().getKey().toString());
        if (mapped.isPresent()) return ingredient(item, mapped.get(), false);
        return switch (item.getType()) {
            case GLASS_BOTTLE, BUCKET -> new IngredientInput.Container(item.getType().getKey().toString(), item.getAmount());
            case WATER_BUCKET -> new IngredientInput.BaseLiquid("minecraft:water", item.getAmount());
            case POTION -> {
                if (item.getItemMeta() instanceof PotionMeta potion && potion.getBasePotionType() == PotionType.WATER
                        && !potion.hasCustomEffects() && !potion.getPersistentDataContainer().has(metadata.key("brew_stage")))
                    yield new IngredientInput.BaseLiquid("minecraft:water", item.getAmount());
                throw new UnsupportedIngredientException("minecraft:potion");
            }
            default -> throw new UnsupportedIngredientException(item.getType().getKey().toString());
        };
    }

    private IngredientInput.Ingredient ingredient(ItemStack item, IngredientDefinition definition, boolean canonical) {
        IngredientModifiers modifiers = definition.defaultModifiers();
        Optional<IngredientProvenance> provenance = Optional.empty();
        if (item.hasItemMeta()) {
            var data = item.getItemMeta().getPersistentDataContainer();
            modifiers = metadata.modifiers(data, modifiers, canonical);
            provenance = metadata.provenance(data);
        }
        return new IngredientInput.Ingredient(new IngredientInstance(definition.id(), metadata.purity(item),
                item.getAmount(), modifiers, provenance));
    }

    /** Clones the item, preserving unrelated PDC/components, lore and amount. */
    public ItemStack write(ItemStack item, IngredientInstance ingredient, boolean washed) {
        Objects.requireNonNull(item);
        var definition = registry.require(ingredient.ingredientId());
        if (item.getAmount() != ingredient.quantity()) throw new IllegalArgumentException("item/ingredient quantity mismatch");
        int previousPurity = metadata.purity(item);
        ItemStack result = item.clone();
        var meta = Objects.requireNonNull(result.getItemMeta(), "ingredient item has no metadata");
        metadata.write(meta.getPersistentDataContainer(), ingredient);
        presentation.apply(meta, definition, ingredient, washed, previousPurity);
        if (!result.setItemMeta(meta)) throw new IllegalArgumentException("ingredient metadata could not be applied");
        return result;
    }

    public IngredientInstance requireIngredient(ItemStack item) {
        if (classify(item) instanceof IngredientInput.Ingredient ingredient) return ingredient.instance();
        throw new UnsupportedIngredientException("container or empty slot");
    }
}
