package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.distillery.*;
import me.arcanebrewery.arcaneBrewery.application.fermentation.*;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EffectProfileMetadataTest {
    private ItemStack base() {
        var item=TestItems.water();item.edit(meta->new FermentationBatchMetadata("arcanebrewery").write(meta,new FermentationBatchState(1,UUID.randomUUID(),
            new DistilleryProcessDraft(Map.of("minecraft:wheat",1.0),Map.of("minecraft:wheat",100.0),"distillation-v2",DistillationControls.DEFAULT),
            new FermentationSettings("arcanebrewery:wild_yeast",22,60,100,0),0,FermentationBatchState.Status.COMPLETED)));
        return item;
    }
    @Test void actualModifierConsumptionMatchesCapturedDoseAndSlotOrderDoesNotMatter() {
        ItemStack[] input={base(),TestItems.stack(Material.IRON_NUGGET,3),TestItems.stack(Material.REDSTONE,2)};
        var op=AlchemyMachineInputs.start(input,"arcanebrewery",0,8000);
        ItemStack[] swapped={input[0],input[2],input[1]};var swappedOp=AlchemyMachineInputs.start(swapped,"arcanebrewery",0,8000);
        assertEquals(op.preparation(),swappedOp.preparation());
        var out=AlchemyMachineInputs.complete(input,"arcanebrewery",op,8000);
        assertEquals(2,out[1].getAmount());assertEquals(1,out[2].getAmount());assertEquals(3,input[1].getAmount());assertEquals(2,input[2].getAmount());
        assertEquals(1.0,op.preparation().additives().get("arcanebrewery:duration_reagent"));
        assertThrows(IllegalArgumentException.class,()->AlchemyMachineInputs.start(out,"arcanebrewery",9000,8000));
    }
    @Test void incompatibleInputsAreRejectedBeforeConsumption() {
        ItemStack[] input={base(),TestItems.stack(Material.SUGAR,2),TestItems.stack(Material.BLAZE_ROD,2)};
        assertThrows(IllegalArgumentException.class,()->AlchemyMachineInputs.start(input,"arcanebrewery",0,8000));assertEquals(2,input[1].getAmount());assertEquals(2,input[2].getAmount());
    }
    @Test void oldSingleEffectMetadataStaysLegacyAndNewProfilesAddNoNativeTooltipEffects() {
        var item=TestItems.water();var meta=item.getItemMeta();var p=meta.getPersistentDataContainer();
        p.set(new NamespacedKey("arcanebrewery","drink_format"),PersistentDataType.INTEGER,4);
        p.set(new NamespacedKey("arcanebrewery","drink_alchemy_effect"),PersistentDataType.STRING,"FIRE_RESISTANCE");
        for(String key:List.of("affinity","potency","instability"))p.set(new NamespacedKey("arcanebrewery","drink_alchemy_"+key),PersistentDataType.DOUBLE,20.0);
        p.set(new NamespacedKey("arcanebrewery","drink_alchemy_dangerous"),PersistentDataType.BYTE,(byte)0);
        var old=new FinalDrinkMetadata("arcanebrewery");assertEquals(AlchemyEffect.FIRE_RESISTANCE,old.readAlchemy(meta).orElseThrow().effect());assertTrue(old.readEffects(meta).isEmpty());
        var data=new EffectProfileMetadata("arcanebrewery");assertFalse(data.newOperation(meta));
        var profile=new EffectProfile(1,EffectCapabilities.STARTER_INFUSER,List.of(new EffectProfile.Entry(ProfileEffect.HASTE,30,0,false)),EffectProfile.Safety.SAFE,100);
        data.write(meta,profile);assertEquals(profile,old.readEffects(meta).orElseThrow());assertEquals(AlchemyEffect.FIRE_RESISTANCE,old.readAlchemy(meta).orElseThrow().effect());
        assertTrue(((PotionMeta)meta).getCustomEffects().isEmpty());assertEquals(1,EffectExecutionPlan.from(profile).size());
        var again=meta.clone();assertEquals(profile,data.read(again).orElseThrow());
    }
    @Test void malformedNewMetadataFailsClosedRatherThanFallingBackToOldBenefits() {
        var meta=TestItems.water().getItemMeta();var data=new EffectProfileMetadata("arcanebrewery");
        meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","drink_effect_profile_format"),PersistentDataType.INTEGER,1);
        assertThrows(IllegalArgumentException.class,()->data.read(meta));
        data.markNewOperation(meta);assertTrue(data.newOperation(meta));
        meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","batch_effect_generation"),PersistentDataType.INTEGER,99);
        assertThrows(IllegalArgumentException.class,()->data.newOperation(meta));
    }
    @Test void executionPlanCoalescesOverlappingFamiliesAndNeverRerolls() {
        var profile=new EffectProfile(1,EffectCapabilities.STARTER_INFUSER,List.of(new EffectProfile.Entry(ProfileEffect.CURSE,12,0,false),new EffectProfile.Entry(ProfileEffect.WEAKNESS,8,0,true)),EffectProfile.Safety.DANGEROUS,30);
        var plan=EffectExecutionPlan.from(profile);assertEquals(2,plan.size());assertEquals(1,plan.stream().filter(a->a.key().equals("WEAKNESS")).count());assertEquals(plan,EffectExecutionPlan.from(profile));
        assertTrue(plan.stream().allMatch(a->a.amplifier()==0&&a.seconds()<=12));
    }
    @Test void extractionCapturesItsPolicyWithoutReinterpretingAnAlreadyRunningBatch() {
        var state=new DistilleryBatchState(1,UUID.randomUUID(),new MachineLocation(UUID.randomUUID(),1,2,3),
            new DistilleryProcessDraft(Map.of("minecraft:wheat",1.0),Map.of("minecraft:wheat",100.0),"distillation-v2",DistillationControls.DEFAULT),0,10000,DistilleryBatchState.Status.PROCESSING);
        var legacy=new DistilleryStateCodec().encodeBase64(state);
        assertEquals(0,DistilleryProcessEnvelope.decode(legacy).effectGeneration());assertEquals(state,DistilleryProcessEnvelope.decode(legacy).state());
        var current=new DistilleryProcessEnvelope(state,1);assertEquals(current,DistilleryProcessEnvelope.decode(current.encode()));
        var ingredients=new DistilleryProcessEnvelope(state,2);assertEquals(ingredients,DistilleryProcessEnvelope.decode(ingredients.encode()));
        assertEquals(legacy,new DistilleryProcessEnvelope(state,0).encode());
        assertThrows(IllegalArgumentException.class,()->DistilleryProcessEnvelope.decode("effects-v99:"+legacy));
    }
    @Test void effectGenerationIsCapturedSeparatelyFromStoredProfileFormat() {
        var data=new EffectProfileMetadata("arcanebrewery");var meta=TestItems.water().getItemMeta();
        assertEquals(0,data.generation(meta));
        data.markGeneration(meta,1);assertEquals(1,data.generation(meta));
        var saved=meta.clone();data.markNewOperation(meta);assertEquals(2,data.generation(meta));
        assertEquals(1,data.generation(saved));
        assertThrows(IllegalArgumentException.class,()->data.markGeneration(meta,3));
        assertEquals(2,data.generation(meta));
        meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","batch_effect_generation"),PersistentDataType.STRING,"2");
        assertThrows(IllegalArgumentException.class,()->data.generation(meta));
    }
    @Test void infusionCompletionKeepsCapturedOldAndNewRulesWhileUnmarkedBasesOptIn() {
        var data=new EffectProfileMetadata("arcanebrewery");
        for(int captured:List.of(0,1,2)){
            var input=base();var meta=input.getItemMeta();
            if(captured>0)data.markGeneration(meta,captured);
            data.captureInfusion(meta);input.setItemMeta(meta);
            ItemStack[] slots={input,TestItems.stack(Material.IRON_NUGGET,1),null};
            var operation=AlchemyMachineInputs.start(slots,"arcanebrewery",0,8000);
            var output=AlchemyMachineInputs.complete(slots,"arcanebrewery",operation,8000);
            assertEquals(captured==1?1:2,data.generation(output[0].getItemMeta()));
            assertNull(output[1]);
            assertEquals(captured==1?1:2,data.generation(slots[0].getItemMeta()));
        }
    }
    @Test void displayOnlyIconsCannotSupplyFreeCatalystsOrIngredientModifiers() {
        var icon=TestItems.stack(Material.IRON_NUGGET,1);icon.edit(me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems::mark);
        ItemStack[] inputs={base(),icon,null};assertThrows(IllegalArgumentException.class,()->AlchemyMachineInputs.start(inputs,"arcanebrewery",0,8000));assertEquals(1,icon.getAmount());
        var wheat=TestItems.stack(Material.WHEAT,1);wheat.edit(me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems::mark);
        var ingredients=new me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientItemAdapter(me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients.registry(),new me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientMetadataCodec("arcanebrewery"));
        assertThrows(IllegalArgumentException.class,()->new me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.MachineIngredientExtractor(ingredients).extract(new ItemStack[]{wheat}));
    }
}
