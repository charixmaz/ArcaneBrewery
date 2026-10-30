package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Canonical identity is versioned; the existing INTEGER purity key remains the sole numeric source. */
public final class IngredientMetadataCodec {
    public static final int VERSION = 1;
    private final String namespace;
    public IngredientMetadataCodec(JavaPlugin plugin) { this("arcanebrewery"); }
    public IngredientMetadataCodec(String namespace) {
        this.namespace = Objects.requireNonNull(namespace);
        key("ingredient_id"); // validate namespace immediately
    }
    public NamespacedKey key(String name) { return new NamespacedKey(namespace, name); }

    public Optional<String> canonicalId(PersistentDataContainer data) {
        if (!data.has(key("ingredient_format")) && !data.has(key("ingredient_id"))) return Optional.empty();
        Integer version = required(data, "ingredient_format", PersistentDataType.INTEGER);
        if (version != VERSION) throw invalid("unsupported ingredient metadata version");
        String id = required(data, "ingredient_id", PersistentDataType.STRING);
        String canonical = IngredientIds.canonical(id);
        if (!canonical.equals(id)) throw invalid("noncanonical ingredient id");
        return Optional.of(id);
    }

    public int purity(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return purity(item.getItemMeta().getPersistentDataContainer());
    }
    public int purity(PersistentDataContainer data) {
        boolean canonical = canonicalId(data).isPresent();
        Integer value = optional(data, "ingredient_purity", PersistentDataType.INTEGER).orElse(null);
        if (canonical && (value == null || value < 0 || value > 100)) throw invalid("invalid canonical purity");
        return value == null ? 0 : Math.max(0, Math.min(100, value));
    }

    public boolean legacyPremium(ItemStack item) {
        if (item == null || item.getType() != Material.WHEAT || !item.hasItemMeta()) return false;
        var data = item.getItemMeta().getPersistentDataContainer();
        return optional(data, "premium_grain_tag", PersistentDataType.STRING)
                .map(value -> value.strip().equalsIgnoreCase("TRUE")).orElse(false);
    }
    public boolean isPremium(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var id = canonicalId(item.getItemMeta().getPersistentDataContainer());
        return id.map(BuiltInIngredients.PREMIUM_GRAIN::equals).orElseGet(() -> legacyPremium(item));
    }
    public boolean legacyWashed(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key("washed_ingredient"), PersistentDataType.STRING);
    }

    public IngredientModifiers modifiers(PersistentDataContainer data, IngredientModifiers defaults, boolean canonical) {
        if (!canonical) return defaults;
        return new IngredientModifiers(optional(data, "ingredient_flavor_bonus", PersistentDataType.DOUBLE).orElse(0.0),
                optional(data, "ingredient_potency_bonus", PersistentDataType.DOUBLE).orElse(0.0));
    }

    public Optional<IngredientProvenance> provenance(PersistentDataContainer data) {
        var source = optional(data, "ingredient_source", PersistentDataType.STRING);
        var producer = optional(data, "ingredient_producer", PersistentDataType.STRING).map(UUID::fromString);
        if (source.isEmpty() && producer.isPresent()) throw invalid("producer requires an ingredient source");
        return source.map(value -> new IngredientProvenance(value, producer));
    }

    public void write(PersistentDataContainer data, IngredientInstance ingredient) {
        data.set(key("ingredient_format"), PersistentDataType.INTEGER, VERSION);
        data.set(key("ingredient_id"), PersistentDataType.STRING, ingredient.ingredientId());
        data.set(key("ingredient_purity"), PersistentDataType.INTEGER, ingredient.purity());
        data.set(key("ingredient_flavor_bonus"), PersistentDataType.DOUBLE, ingredient.modifiers().flavor());
        data.set(key("ingredient_potency_bonus"), PersistentDataType.DOUBLE, ingredient.modifiers().potency());
        data.remove(key("premium_grain_tag"));
        data.remove(key("ingredient_source"));
        data.remove(key("ingredient_producer"));
        ingredient.provenance().ifPresent(source -> {
            data.set(key("ingredient_source"), PersistentDataType.STRING, source.source());
            source.producer().ifPresent(player -> data.set(key("ingredient_producer"), PersistentDataType.STRING, player.toString()));
        });
        // Do not synthesize or erase the old washed marker: the dormant legacy task still consumes it.
    }

    private <P, C> Optional<C> optional(PersistentDataContainer data, String name, PersistentDataType<P, C> type) {
        var key = key(name);
        if (data.has(key) && !data.has(key, type)) throw invalid("ingredient metadata type mismatch");
        return Optional.ofNullable(data.get(key, type));
    }
    private <P, C> C required(PersistentDataContainer data, String name, PersistentDataType<P, C> type) {
        return optional(data, name, type).orElseThrow(() -> invalid("incomplete ingredient metadata"));
    }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
