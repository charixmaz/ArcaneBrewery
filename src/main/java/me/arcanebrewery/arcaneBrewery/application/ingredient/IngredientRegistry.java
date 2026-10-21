package me.arcanebrewery.arcaneBrewery.application.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import java.util.*;

/** Immutable catalog and external-key mapping. Replace the catalog at configuration boundaries. */
public final class IngredientRegistry {
    private final Map<String, IngredientDefinition> definitions;
    private final Map<String, String> mappings;

    public IngredientRegistry(Collection<IngredientDefinition> definitions, Map<String, String> mappings) {
        var catalog = new TreeMap<String, IngredientDefinition>();
        for (var definition : definitions) {
            if (catalog.putIfAbsent(definition.id(), definition) != null)
                throw new IllegalArgumentException("duplicate ingredient definition: " + definition.id());
        }
        var external = new TreeMap<String, String>();
        mappings.forEach((key, id) -> {
            String normalized = IngredientIds.canonical(key);
            String ingredient = IngredientIds.canonical(id);
            if (!catalog.containsKey(ingredient)) throw new IllegalArgumentException("mapping references unknown ingredient");
            if (external.putIfAbsent(normalized, ingredient) != null)
                throw new IllegalArgumentException("duplicate external item mapping");
        });
        this.definitions = Collections.unmodifiableMap(catalog);
        this.mappings = Collections.unmodifiableMap(external);
    }

    public Map<String, IngredientDefinition> definitions() { return definitions; }
    public Map<String, String> externalMappings() { return mappings; }
    public IngredientDefinition require(String id) {
        var definition = definitions.get(IngredientIds.canonical(id));
        if (definition == null) throw new UnsupportedIngredientException(id);
        return definition;
    }
    public Optional<IngredientDefinition> resolveExternal(String key) {
        return Optional.ofNullable(mappings.get(IngredientIds.canonical(key))).map(definitions::get);
    }
}
