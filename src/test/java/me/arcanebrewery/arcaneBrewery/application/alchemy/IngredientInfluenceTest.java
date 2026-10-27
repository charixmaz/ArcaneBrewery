package me.arcanebrewery.arcaneBrewery.application.alchemy;

import me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import me.arcanebrewery.arcaneBrewery.application.research.SpecialistProcess;
import me.arcanebrewery.arcaneBrewery.application.research.SpecialistType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static me.arcanebrewery.arcaneBrewery.application.alchemy.ProfileEffect.*;
import static org.junit.jupiter.api.Assertions.*;

class IngredientInfluenceTest {
    private final EffectProfileCalculator calculator = new EffectProfileCalculator();
    private ProcessProfile process(Map<String, Double> ingredients, String... catalysts) {
        var additives = new TreeMap<String, Double>();
        for (var material : catalysts) additives.merge(EffectProfilesTest.id(material), 1.0, Double::sum);
        var reference = EffectProfilesTest.process(additives);
        return new ProcessProfile(ingredients, reference.preparationMethod(), reference.fermentation(),
                reference.distillation(), reference.aging(), additives);
    }
    private EffectProfile brew(String ingredient, String... catalysts) {
        return brew(Map.of("minecraft:" + ingredient, 1.0), catalysts);
    }
    private EffectProfile brew(Map<String, Double> ingredients, String... catalysts) {
        return calculator.calculate(process(ingredients, catalysts), 100, 0, 100);
    }
    private EffectProfile.Entry effect(EffectProfile p, ProfileEffect effect) {
        return p.effects().stream().filter(e -> e.effect() == effect).findFirst().orElseThrow();
    }

    @Test void sameMiningCatalystGivesDifferentStrengthAndDurationByBase() {
        var wheat = brew("wheat", "iron_nugget");
        var potato = brew("potato", "iron_nugget");
        var cane = brew("sugar_cane", "iron_nugget");
        assertEquals(0, effect(wheat, HASTE).amplifier());
        assertEquals(1, effect(potato, HASTE).amplifier());
        assertEquals(0, effect(cane, HASTE).amplifier());
        assertTrue(effect(cane, HASTE).seconds() > effect(potato, HASTE).seconds());
        assertEquals(3, Set.of(wheat, potato, cane).size());
    }

    @Test void potatoCaneBlendTradesPeakDurationForMiningStrengthAndRatiosMatter() {
        var potato = effect(brew("potato", "iron_nugget"), HASTE);
        var cane = effect(brew("sugar_cane", "iron_nugget"), HASTE);
        var balanced = effect(brew(Map.of("minecraft:potato", 1.0, "minecraft:sugar_cane", 1.0), "iron_nugget"), HASTE);
        var dilute = effect(brew(Map.of("minecraft:potato", 1.0, "minecraft:sugar_cane", 3.0), "iron_nugget"), HASTE);
        assertEquals(1, balanced.amplifier());
        assertTrue(balanced.seconds() > potato.seconds());
        assertTrue(balanced.seconds() < cane.seconds());
        assertEquals(0, dilute.amplifier());
        assertTrue(dilute.seconds() > balanced.seconds());
    }

    @Test void honeycombShortensDrawbacksAndRaisesProfileStabilityAtStrengthCost() {
        var pure = calculator.calculate(process(Map.of("minecraft:potato", 1.0), "iron_nugget"), 100, 0, 70);
        var mixed = calculator.calculate(process(Map.of("minecraft:potato", 1.0, "minecraft:honeycomb", 1.0), "iron_nugget"), 100, 0, 70);
        assertTrue(mixed.stability() > pure.stability());
        assertTrue(effect(mixed, HUNGER).seconds() < effect(pure, HUNGER).seconds());
        assertEquals(1, effect(pure, HASTE).amplifier());
        assertEquals(0, effect(mixed, HASTE).amplifier());
        assertFalse(mixed.effects().stream().anyMatch(e -> Set.of(HALLUCINATION, CURSE, WITHER).contains(e.effect())));
    }

    @Test void familySpecialistsBeatTheSameBaseForEverything() {
        assertTrue(effect(brew("apple", "glistering_melon_slice"), REGENERATION).seconds()
                > effect(brew("potato", "glistering_melon_slice"), REGENERATION).seconds());
        assertTrue(effect(brew("apple", "glistering_melon_slice"), REGENERATION).seconds()
                > effect(brew("sugar_cane", "glistering_melon_slice"), REGENERATION).seconds());
        assertTrue(effect(brew("sweet_berries", "amethyst_shard"), INVISIBILITY).seconds()
                > effect(brew("apple", "amethyst_shard"), INVISIBILITY).seconds());
        assertTrue(effect(brew("sweet_berries", "glowstone_dust"), NIGHT_VISION).seconds()
                > effect(brew("sugar_cane", "glowstone_dust"), NIGHT_VISION).seconds());
        assertTrue(effect(brew("sugar_cane", "sugar"), SPEED).amplifier()
                > effect(brew("potato", "sugar"), SPEED).amplifier());
        assertTrue(effect(brew("golden_apple", "golden_apple"), ABSORPTION).seconds()
                > effect(brew("wheat", "golden_apple"), ABSORPTION).seconds());
    }

    @Test void poorAffinityCannotBeBypassedWithPotencyReagentOrDuplicateCatalysts() {
        assertEquals(0, effect(brew("apple", "iron_nugget", "glowstone"), HASTE).amplifier());
        assertEquals(0, effect(brew("wheat", "iron_nugget", "iron_nugget"), HASTE).amplifier());
        assertEquals(1, effect(brew("potato", "iron_nugget", "glowstone"), HASTE).amplifier());
        assertTrue(effect(brew("potato", "iron_nugget", "redstone"), HASTE).seconds()
                > effect(brew("potato", "iron_nugget"), HASTE).seconds());
    }

    @Test void scaledProportionsAndReversedSlotsAreExactlyReproducibleWithoutNewIdentityRules() {
        var a = new LinkedHashMap<String, Double>(); a.put("minecraft:potato", 1.0); a.put("minecraft:sugar_cane", 2.0);
        var b = new LinkedHashMap<String, Double>(); b.put("minecraft:sugar_cane", 4.0); b.put("minecraft:potato", 2.0);
        assertEquals(brew(a, "iron_nugget", "lapis_lazuli"), brew(b, "lapis_lazuli", "iron_nugget"));
        assertEquals(FormulationIdentity.from(process(a, "iron_nugget")), FormulationIdentity.from(process(b, "iron_nugget")));
        assertNotEquals(FormulationIdentity.from(process(a, "iron_nugget")), FormulationIdentity.from(process(Map.of("minecraft:potato", 2.0, "minecraft:sugar_cane", 1.0), "iron_nugget")));
    }

    @Test void bothFamiliesOfAMultiEffectElixirUseTheirOwnIngredientSuitability() {
        var potato = brew("potato", "iron_nugget", "glowstone_dust");
        var berries = brew("sweet_berries", "iron_nugget", "glowstone_dust");
        assertEquals(1, effect(potato, HASTE).amplifier());
        assertEquals(0, effect(berries, HASTE).amplifier());
        assertTrue(effect(berries, NIGHT_VISION).seconds() > effect(potato, NIGHT_VISION).seconds());
        assertTrue(effect(potato, HUNGER).sideEffect());
    }

    @Test void everyObtainableBaseHasAnExplicitDistinctMiningOutcomeAndUnknownBasesAreConservative() {
        assertEquals(8, IngredientAffinities.supportedIngredients().size());
        var results = new HashSet<EffectProfile>();
        for (var id : IngredientAffinities.supportedIngredients()) results.add(brew(Map.of(id, 1.0), "iron_nugget"));
        assertEquals(8, results.size());
        var unknown = brew(Map.of("custom:grain", 1.0), "iron_nugget");
        assertEquals(0, effect(unknown, HASTE).amplifier());
        assertTrue(effect(unknown, HASTE).seconds() < effect(brew("wheat", "iron_nugget"), HASTE).seconds());
    }

    @Test void generationsPreserveOldResultsAndStoredProfilesDoNotRecalculate() {
        var p = process(Map.of("minecraft:wheat", 1.0), "iron_nugget");
        var old = calculator.calculate(p, 100, 0, 100, 1);
        assertEquals(new EffectProfile.Entry(HASTE, 26, 1, false), effect(old, HASTE));
        assertEquals(new EffectProfile.Entry(HUNGER, 15, 0, true), effect(old, HUNGER));
        assertNotEquals(old, calculator.calculate(p, 100, 0, 100, 2));
        var codec = new EffectProfileCodec();
        assertEquals(old, codec.decode(codec.encode(old)));
        assertEquals(old, calculator.calculate(p, 100, 0, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(p, 100, 0, 100, 3));
    }

    @Test void zeroQualityConflictsAndPoorEquipmentCannotBeOverriddenByIngredients() {
        for (var id : IngredientAffinities.supportedIngredients()) {
            var p = process(Map.of(id, 1.0), "iron_nugget");
            assertTrue(calculator.calculate(p, 0, 0, 100).effects().isEmpty());
            assertEquals(EffectProfile.Safety.DANGEROUS, calculator.calculate(p, 100, 0, 0).safety());
            assertThrows(IllegalArgumentException.class, () -> brew(Map.of(id, 1.0), "sugar", "blaze_rod"));
        }
    }

    @Test void allSupportedBasePairsAndRawReagentsRespectUnchangedStarterAndSpecialistCaps() {
        for (var first : IngredientAffinities.supportedIngredients()) for (var second : IngredientAffinities.supportedIngredients()) {
            var base = new TreeMap<String, Double>(); base.merge(first, 2.0, Double::sum); base.merge(second, 1.0, Double::sum);
            for (var reagent : ReagentRegistry.all()) if (reagent.role() == ReagentRole.EFFECT_CATALYST) {
                var profile = brew(base, reagent.externalId().substring("minecraft:".length()));
                for (var e : profile.effects()) {
                    var cap = EffectCapabilities.STARTER_INFUSER.cap(e.effect());
                    assertTrue(e.seconds() <= cap.seconds()); assertTrue(e.amplifier() <= cap.amplifier());
                }
                boolean extendable = profile.effects().stream().anyMatch(e -> !e.sideEffect()
                        && (e.amplifier() > 0 || e.seconds() < EffectCapabilities.DURATION_INFUSER.cap(e.effect()).seconds()));
                if (extendable) {
                    var duration = SpecialistProcess.apply(SpecialistType.DURATION_INFUSER, profile, 0, null);
                    assertTrue(duration.profile().effects().stream().allMatch(e -> e.seconds() <= duration.profile().capability().cap(e.effect()).seconds()));
                    assertThrows(IllegalStateException.class, () -> SpecialistProcess.apply(SpecialistType.DURATION_INFUSER, duration.profile(), duration.history(), null));
                } else assertThrows(IllegalArgumentException.class, () -> SpecialistProcess.apply(SpecialistType.DURATION_INFUSER, profile, 0, null));
                if (profile.effects().stream().anyMatch(e -> Set.of(HASTE, SPEED, JUMP_BOOST, STRENGTH).contains(e.effect()))) {
                    var concentrated = SpecialistProcess.apply(SpecialistType.ESSENCE_CONCENTRATOR, profile, 0, null);
                    for (var e : concentrated.profile().effects()) assertTrue(e.amplifier() <= EffectCapabilities.ESSENCE_CONCENTRATOR.cap(e.effect()).amplifier());
                }
            }
        }
    }

    @Test void innateEffectsRemainModestAndCatalystsDoNotInventFreeExtraFamilies() {
        var natural = brew("potato");
        assertEquals(0, effect(natural, HASTE).amplifier());
        assertTrue(effect(natural, HASTE).seconds() <= 8);
        var steered = brew("potato", "blaze_powder");
        assertTrue(steered.effects().stream().anyMatch(e -> e.effect() == FIRE_RESISTANCE));
        assertTrue(steered.effects().stream().noneMatch(e -> e.effect() == HASTE));
    }

    @Test void realWorkmanshipAndMaturationPipelineStillMakesMiningBasesMeaningfullyDifferent() {
        var registry=me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients.registry();
        var results=new HashMap<String,EffectProfile>();
        for(String ingredient:List.of("wheat","potato","sugar_cane")) {
            String id="minecraft:"+ingredient;
            var draft=new me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryProcessDraft(
                Map.of(id,1.0),Map.of(id,100.0),"distillation-v2",me.arcanebrewery.arcaneBrewery.application.distillery.DistillationControls.DEFAULT);
            var source=new me.arcanebrewery.arcaneBrewery.application.fermentation.FermentedProcessDraft(
                draft,EffectProfilesTest.process(Map.of()).fermentation());
            var additives=Map.of(EffectProfilesTest.id("iron_nugget"),1.0);
            long target=me.arcanebrewery.arcaneBrewery.application.aging.RecipeMaturation.target(source,additives);
            var aging=new me.arcanebrewery.arcaneBrewery.application.aging.AgingBatchState(1,UUID.randomUUID(),
                new me.arcanebrewery.arcaneBrewery.application.aging.AgingSettings("oak",0,18,100),0,target,true);
            var lots=Map.of(id,new me.arcanebrewery.arcaneBrewery.domain.quality.IngredientLot(registry.require(id).profile(),100));
            var harvest=new me.arcanebrewery.arcaneBrewery.application.aging.AgingService().finish(source,aging,
                target*1000,lots,me.arcanebrewery.arcaneBrewery.application.aging.AgingBalance.wood("oak"),0,
                me.arcanebrewery.arcaneBrewery.application.aging.AgingBalance.targets(),42,additives,100);
            assertTrue(harvest.assessment().score()>=90);
            results.put(ingredient,calculator.calculate(harvest.process(),harvest.assessment().score(),0,100));
        }
        assertEquals(0,effect(results.get("wheat"),HASTE).amplifier());
        assertEquals(1,effect(results.get("potato"),HASTE).amplifier());
        assertEquals(0,effect(results.get("sugar_cane"),HASTE).amplifier());
        assertTrue(effect(results.get("sugar_cane"),HASTE).seconds()>effect(results.get("potato"),HASTE).seconds());
    }
}
