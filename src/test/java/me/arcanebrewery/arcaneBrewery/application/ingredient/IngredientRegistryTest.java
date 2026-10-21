package me.arcanebrewery.arcaneBrewery.application.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IngredientRegistryTest {
    @Test
    void builtInDefinitionsAndExternalMappingsCoverEveryLegacyMaterial() {
        var registry = BuiltInIngredients.registry();
        var expected = Set.of("wheat", "sugar_cane", "sugar", "apple", "golden_apple", "potato",
                "sweet_berries", "honeycomb", "kelp", "melon_slice", "cactus", "brown_mushroom",
                "red_mushroom", "cocoa_beans", "blaze_powder", "chorus_fruit", "glistering_melon_slice",
                "glowstone_dust", "prismarine_crystals", "amethyst_shard", "netherite_scrap", "echo_shard",
                "shulker_shell", "wither_skeleton_skull");
        assertEquals(25, registry.definitions().size());
        assertEquals(24, registry.externalMappings().size());
        for (String key : expected) {
            String id = "minecraft:" + key;
            assertEquals(id, registry.resolveExternal(id).orElseThrow().id());
        }
        assertTrue(registry.require(BuiltInIngredients.PREMIUM_GRAIN).premium());
        assertNotEquals(registry.require("minecraft:wheat").profile(), registry.require(BuiltInIngredients.PREMIUM_GRAIN).profile());
    }

    @Test
    void customNamespacedDefinitionsAndExternalAliasesAreSupported() {
        var definition = new IngredientDefinition(new IngredientProfile("server:moon_grain", 50, 70, 10, 5, 60, 0, 30),
                "Moon Grain", false, IngredientModifiers.NONE);
        var registry = new IngredientRegistry(List.of(definition), Map.of("itemsadder:pack/moon_grain", definition.id()));
        assertEquals(definition, registry.require(" SERVER:MOON_GRAIN "));
        assertEquals(definition, registry.resolveExternal("itemsadder:pack/moon_grain").orElseThrow());
    }

    @Test
    void duplicateDefinitionsMappingsAndMissingTargetsAreRejected() {
        var wheat = BuiltInIngredients.registry().require("minecraft:wheat");
        assertThrows(IllegalArgumentException.class, () -> new IngredientRegistry(List.of(wheat, wheat), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new IngredientRegistry(List.of(wheat), Map.of("minecraft:paper", "unknown:grain")));
        assertThrows(IllegalArgumentException.class, () -> new IngredientRegistry(List.of(wheat),
                Map.of("minecraft:wheat", wheat.id(), " MINECRAFT:WHEAT ", wheat.id())));
        assertThrows(IllegalArgumentException.class, () -> IngredientIds.canonical("wheat"));
        assertThrows(IllegalArgumentException.class, () -> IngredientIds.canonical("mod:bad id"));
    }

    @Test
    void unsupportedExternalItemDoesNotBecomeAnIngredient() {
        var registry = BuiltInIngredients.registry();
        assertTrue(registry.resolveExternal("minecraft:stone").isEmpty());
        assertThrows(UnsupportedIngredientException.class, () -> registry.require("minecraft:stone"));
    }

    @Test
    void catalogsAreDefensiveImmutableAndReplaceableForFutureConfiguration() {
        var original = BuiltInIngredients.registry();
        var definitions = new ArrayList<>(original.definitions().values());
        var mappings = new HashMap<>(original.externalMappings());
        var configured = new IngredientRegistry(definitions, mappings);
        definitions.clear(); mappings.clear();
        assertEquals(25, configured.definitions().size());
        assertThrows(UnsupportedOperationException.class, () -> configured.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> configured.externalMappings().clear());
    }

    @Test
    void ingredientInstancesValidateQuantitiesPurityAndModifiers() {
        var raw = new IngredientInstance("minecraft:wheat", -1, 4, IngredientModifiers.NONE, Optional.empty());
        assertEquals(0, raw.purity());
        assertEquals(25, raw.washed().purity());
        assertEquals(100, new IngredientInstance("minecraft:wheat", Integer.MAX_VALUE, 1,
                IngredientModifiers.NONE, Optional.empty()).washed().purity());
        assertThrows(IllegalArgumentException.class, () -> raw.withQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> new IngredientModifiers(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new IngredientModifiers(11, 0));
    }

    @Test
    void domainAndApplicationIngredientPackagesHaveNoBukkitOrPaperDependencies() throws Exception {
        for (String segment : List.of("domain/ingredient", "application/ingredient")) {
            try (var paths = Files.walk(Path.of("src/main/java/me/arcanebrewery/arcaneBrewery", segment))) {
                for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("org.bukkit.") || source.contains("io.papermc."), path.toString());
                }
            }
        }
    }
}
