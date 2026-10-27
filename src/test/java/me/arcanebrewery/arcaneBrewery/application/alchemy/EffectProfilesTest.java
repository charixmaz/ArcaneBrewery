package me.arcanebrewery.arcaneBrewery.application.alchemy;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EffectProfilesTest {
    private final EffectProfileCalculator calculator=new EffectProfileCalculator();
    // Frozen generation-1 regression expectations; new generation has separate gameplay tests.
    private EffectProfile legacy(ProcessProfile p,double quality,double contamination,double stability) {
        return calculator.calculate(p,quality,contamination,stability,1);
    }
    static ProcessProfile process(Map<String,Double> additives) {
        return new ProcessProfile(Map.of("minecraft:wheat",1.0),"distillation-v2",new FermentationProfile("arcanebrewery:wild_yeast",22,60,100,0),new DistillationProfile(10,80,10,78,1),new AgingProfile("oak",120,0,18,100),additives);
    }
    static String id(String material){return ReagentRegistry.external("minecraft:"+material).orElseThrow().additiveId();}
    private EffectProfile profile(String... material) {var adds=new HashMap<String,Double>();for(var m:material)adds.merge(id(m),1.0,Double::sum);return legacy(process(adds),100,0,100);}
    @Test void registryHasTwentySixDistinctSupportedRawInputsAndExplicitRoles() {
        assertEquals(26,ReagentRegistry.all().size());assertEquals(26,ReagentRegistry.all().stream().map(ReagentDefinition::id).distinct().count());
        assertEquals(20,ReagentRegistry.all().stream().filter(d->d.role()==ReagentRole.EFFECT_CATALYST).count());
        assertEquals(3,ReagentRegistry.all().stream().filter(d->d.role()==ReagentRole.DURATION_REAGENT).count());
        assertEquals(1,ReagentRegistry.all().stream().filter(d->d.role()==ReagentRole.POTENCY_REAGENT).count());
        assertEquals(2,ReagentRegistry.all().stream().filter(d->d.role()==ReagentRole.STABILIZER).count());
        assertTrue(ReagentRegistry.all().stream().noneMatch(d->d.role()==ReagentRole.FUTURE_PURIFICATION));
        assertEquals("arcanebrewery:fire_catalyst",id("blaze_powder"));assertNotEquals(profile("blaze_powder"),profile("blaze_rod"));
    }
    @Test void sameConditionsAndReorderedInputsGiveTheSameMultiEffectProfile() {
        var a=new LinkedHashMap<String,Double>();a.put(id("iron_nugget"),1.0);a.put(id("prismarine_crystals"),1.0);
        var b=new LinkedHashMap<String,Double>();b.put(id("prismarine_crystals"),1.0);b.put(id("iron_nugget"),1.0);
        var first=legacy(process(a),92,0,90);assertEquals(first,legacy(process(b),92,0,90));
        assertEquals(FormulationIdentity.from(process(a)),FormulationIdentity.from(process(b)));
        assertTrue(first.effects().stream().anyMatch(e->e.effect()==ProfileEffect.HASTE));assertTrue(first.effects().stream().anyMatch(e->e.effect()==ProfileEffect.WATER_BREATHING));
        assertTrue(first.effects().stream().anyMatch(e->e.effect()==ProfileEffect.HUNGER&&e.sideEffect()));
        assertTrue(first.effects().stream().noneMatch(e->Set.of(ProfileEffect.HALLUCINATION,ProfileEffect.CURSE).contains(e.effect())));
    }
    @Test void durationAndPotencyHaveRealTradeoffsAndFamilySpecificCaps() {
        var ordinary=profile("iron_nugget").effects().getFirst();var longer=profile("iron_nugget","redstone").effects().getFirst();var stronger=profile("iron_nugget","glowstone").effects().getFirst();
        assertTrue(longer.seconds()>ordinary.seconds());assertTrue(longer.amplifier()<ordinary.amplifier());
        assertEquals(1,stronger.amplifier());assertTrue(stronger.seconds()<longer.seconds());
        for(var d:ReagentRegistry.all())if(d.role()==ReagentRole.EFFECT_CATALYST)for(var extra:List.of("redstone_block","glowstone","amethyst_block")) {
            var p=profile(d.externalId().substring(10),extra);
            for(var e:p.effects()){var cap=EffectCapabilities.STARTER_INFUSER.cap(e.effect());assertTrue(e.seconds()<=cap.seconds());assertTrue(e.amplifier()<=cap.amplifier());}
        }
        assertTrue(profile("blaze_rod","glowstone").effects().stream().filter(e->e.effect()==ProfileEffect.STRENGTH).allMatch(e->e.amplifier()==0&&e.seconds()<=30));
    }
    @Test void conflictsAndModifierOnlyRecipesFailRatherThanCancelBenefits() {
        assertThrows(IllegalArgumentException.class,()->profile("sugar","blaze_rod"));
        assertThrows(IllegalArgumentException.class,()->profile("shulker_shell","phantom_membrane"));
        assertThrows(IllegalArgumentException.class,()->profile("wither_skeleton_skull","ghast_tear"));
        assertThrows(IllegalArgumentException.class,()->profile("redstone","glowstone"));
    }
    @Test void stabilizerUsesCapturedEquipmentAndDoesNotEraseContaminationHistory() {
        var dirty=legacy(process(Map.of(id("iron_nugget"),1.0)),90,30,45);
        var stabilized=legacy(process(Map.of(id("iron_nugget"),1.0,id("amethyst_block"),1.0)),90,30,45);
        assertTrue(stabilized.stability()>dirty.stability());assertNotEquals(EffectProfile.Safety.SAFE,stabilized.safety());
        var model=new AlchemyFailureModel();assertTrue(model.assess(new AlchemyPreparation(Map.of(id("iron_nugget"),1.0,id("amethyst_block"),1.0)),new AlchemyMachineCondition(60,20)).instability()
            <model.assess(new AlchemyPreparation(Map.of(id("iron_nugget"),1.0)),new AlchemyMachineCondition(60,20)).instability());
    }
    @Test void ruinedOrUnfinishedBatchesHaveNoProfileBenefitsAndDangerIsDeliberate() {
        assertTrue(legacy(process(Map.of(id("iron_nugget"),1.0)),0,0,100).effects().isEmpty());
        assertEquals(EffectProfile.Safety.DANGEROUS,profile("wither_skeleton_skull").safety());
        assertEquals(EffectProfile.Safety.SAFE,profile("sugar").safety());
        assertTrue(profile("blaze_rod").effects().stream().anyMatch(e->e.effect()==ProfileEffect.SLOWNESS&&e.seconds()<=12));
    }
    @Test void codecIsVersionedBoundedAndRejectsInvalidProfiles() {
        var p=profile("iron_nugget","prismarine_crystals");var codec=new EffectProfileCodec();assertEquals(p,codec.decode(codec.encode(p)));
        var bytes=codec.encode(p);bytes[7]=99;assertThrows(IllegalArgumentException.class,()->codec.decode(bytes));
        assertThrows(IllegalArgumentException.class,()->codec.decode(new byte[4097]));
        assertThrows(IllegalArgumentException.class,()->new EffectProfile(1,EffectCapabilities.STARTER_INFUSER,List.of(new EffectProfile.Entry(ProfileEffect.STRENGTH,600,4,false)),EffectProfile.Safety.SAFE,100));
        assertThrows(IllegalArgumentException.class,()->legacy(process(Map.of(id("sugar"),1.0)),Double.NaN,0,100));
        assertThrows(IllegalArgumentException.class,()->legacy(process(Map.of(id("sugar"),100.0)),100,0,100));
    }
    @Test void batchUuidAndSeedVariationCannotRerollBenefits() {
        var registry=me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients.registry();
        var draft=new me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryProcessDraft(Map.of("minecraft:wheat",1.0),Map.of("minecraft:wheat",100.0),"distillation-v2",me.arcanebrewery.arcaneBrewery.application.distillery.DistillationControls.DEFAULT);
        var source=new me.arcanebrewery.arcaneBrewery.application.fermentation.FermentedProcessDraft(draft,new FermentationProfile("arcanebrewery:wild_yeast",22,60,100,0));
        var settings=new me.arcanebrewery.arcaneBrewery.application.aging.AgingSettings("oak",0,18,100);
        var lots=Map.of("minecraft:wheat",new me.arcanebrewery.arcaneBrewery.domain.quality.IngredientLot(registry.require("minecraft:wheat").profile(),100));
        var service=new me.arcanebrewery.arcaneBrewery.application.aging.AgingService();EffectProfile expected=null;
        for(long seed:List.of(1L,2L,100L)) {
            var age=new me.arcanebrewery.arcaneBrewery.application.aging.AgingBatchState(1,UUID.randomUUID(),settings,0,120);
            var batch=service.finish(source,age,125000,lots,me.arcanebrewery.arcaneBrewery.application.aging.AgingBalance.wood("oak"),0,me.arcanebrewery.arcaneBrewery.application.aging.AgingBalance.targets(),seed,Map.of(id("iron_nugget"),1.0));
            var effect=legacy(batch.process(),batch.assessment().score(),batch.simulation().quality().contamination(),100);
            if(expected==null)expected=effect;else assertEquals(expected,effect);
        }
    }
    @Test void ordinaryNaturalBrewsDoNotFallBackToAffinityHallucinations() {
        for(String ingredient:List.of("minecraft:golden_apple","minecraft:apple","minecraft:wheat","minecraft:sugar_cane","minecraft:honeycomb","minecraft:sweet_berries")) {
            var original=process(Map.of());var p=new ProcessProfile(Map.of(ingredient,1.0),original.preparationMethod(),original.fermentation(),original.distillation(),original.aging(),Map.of());
            var result=legacy(p,100,0,100);
            assertEquals(EffectProfile.Safety.SAFE,result.safety());assertTrue(result.effects().stream().noneMatch(e->Set.of(ProfileEffect.HALLUCINATION,ProfileEffect.CURSE,ProfileEffect.WITHER).contains(e.effect())));
            assertTrue(result.effects().stream().allMatch(e->e.amplifier()==0&&e.seconds()<=20));
        }
        var old=process(Map.of());var fruit=new ProcessProfile(Map.of("minecraft:golden_apple",1.0),old.preparationMethod(),old.fermentation(),old.distillation(),old.aging(),Map.of());
        assertTrue(legacy(fruit,100,0,100).effects().stream().anyMatch(e->e.effect()==ProfileEffect.ABSORPTION));
    }
}
