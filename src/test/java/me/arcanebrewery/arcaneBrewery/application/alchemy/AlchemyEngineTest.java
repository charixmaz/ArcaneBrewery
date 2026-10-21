package me.arcanebrewery.arcaneBrewery.application.alchemy;

import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.quality.BatchQuality;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlchemyEngineTest {
    private final AlchemyEngine engine = new AlchemyEngine(BuiltInIngredients.registry());

    @Test
    void blazeDominantRecipeProducesFireResistanceFamily() {
        var result = engine.evaluate(process(Map.of("minecraft:blaze_powder", 1.0), Map.of()), quality(80, 10));
        assertEquals(AlchemyEffect.FIRE_RESISTANCE, result.effect());
        assertTrue(result.potency() > 0);
    }

    @Test
    void chorusFruitProducesTeleportationFamilyDeterministically() {
        var process = process(Map.of("minecraft:chorus_fruit", 1.0), Map.of());
        assertEquals(engine.evaluate(process, quality(70, 10)).effect(),
                engine.evaluate(process, quality(20, 80)).effect());
        assertEquals(AlchemyEffect.TELEPORTATION, engine.evaluate(process, quality(70, 10)).effect());
    }

    @Test
    void qualityChangesPotencyAndInstabilityNotEffectIdentity() {
        var process = process(Map.of("minecraft:echo_shard", 1.0), Map.of());
        var clean = engine.evaluate(process, quality(90, 0));
        var dirty = engine.evaluate(process, quality(25, 80));
        assertEquals(clean.effect(), dirty.effect());
        assertNotEquals(clean.potency(), dirty.potency());
        assertNotEquals(clean.instability(), dirty.instability());
    }

    @Test
    void explicitAdditiveCanSteerEffectFamily() {
        var result = engine.evaluate(
                process(Map.of("minecraft:wheat", 1.0), Map.of("arcanebrewery:invisibility_catalyst", 1.0)),
                quality(70, 0));
        assertEquals(AlchemyEffect.INVISIBILITY, result.effect());
    }

    @Test
    void mundaneRecipeHasNoEffect() {
        var result = engine.evaluate(process(Map.of("minecraft:wheat", 1.0), Map.of()), quality(80, 0));
        assertEquals(AlchemyEffect.NONE, result.effect());
        assertEquals(0, result.potency());
    }

    @Test
    void toxicHighAffinityRecipeCanBecomeDangerous() {
        var result = engine.evaluate(
                process(Map.of("minecraft:wither_skeleton_skull", 1.0), Map.of()),
                quality(20, 80));
        assertEquals(AlchemyEffect.WITHER, result.effect());
        assertTrue(result.dangerousReaction());
    }

    private static ProcessProfile process(Map<String, Double> ingredients, Map<String, Double> additives) {
        return new ProcessProfile(
                ingredients,
                "distillation",
                new FermentationProfile("arcanebrewery:wild_yeast", 22, 60, 85, 10),
                new DistillationProfile(10, 80, 10, 78, 1),
                new AgingProfile("oak", 120, 12, 18, 95),
                additives
        );
    }

    private static BatchQuality quality(double magicalPotency, double contamination) {
        return new BatchQuality(80, 80, 80, 70, contamination, magicalPotency);
    }
}
