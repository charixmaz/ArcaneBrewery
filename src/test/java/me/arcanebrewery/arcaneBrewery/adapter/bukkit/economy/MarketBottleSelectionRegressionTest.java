package me.arcanebrewery.arcaneBrewery.adapter.bukkit.economy;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.EffectProfileMetadata;
import me.arcanebrewery.arcaneBrewery.application.aging.AgingResult;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.economy.DrinkValueCalculator;
import me.arcanebrewery.arcaneBrewery.application.research.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.quality.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static me.arcanebrewery.arcaneBrewery.gameplay.GameplayV2Test.*;
import static org.junit.jupiter.api.Assertions.*;

class MarketBottleSelectionRegressionTest {
    @Test void upgradedBottleCannotMatchAnOrdinaryQuotedBottleDespiteEqualMarketValue() {
        var p=perfect();var q=new BatchQuality(100,80,80,100,0,50);
        var a=new AlchemyResult(AlchemyEffect.NONE,0,0,0,false);
        var result=new AgingResult(p,new BatchSimulationResult(DrinkFingerprint.from(p),q,new Contamination(0,0)));
        var plain=TestItems.water();var metadata=new EffectProfileMetadata("arcanebrewery");
        var initial=new EffectProfile(1,EffectCapabilities.STARTER_INFUSER,List.of(
                new EffectProfile.Entry(ProfileEffect.HASTE,20,1,false),
                new EffectProfile.Entry(ProfileEffect.HUNGER,15,0,true)),EffectProfile.Safety.SAFE,100);
        plain.edit(m->{new FinalDrinkMetadata("arcanebrewery").write(m,result,a,new DrinkValueCalculator().calculate(p,q,a));metadata.write(m,initial);});
        var upgraded=plain.clone();
        var extended=SpecialistProcess.apply(SpecialistType.DURATION_INFUSER,initial,0,null).profile();
        upgraded.edit(m->metadata.write(m,extended));
        assertNotEquals(metadata.read(plain.getItemMeta()),metadata.read(upgraded.getItemMeta()));
        var reader=new CanonicalMarketBottleReader("arcanebrewery");
        assertEquals(reader.read(plain).orElseThrow(),reader.read(upgraded).orElseThrow());
        assertTrue(BukkitMarketAssetPort.same(plain,plain.clone()));
        assertFalse(BukkitMarketAssetPort.same(upgraded,plain));
        assertEquals(240,extended.effects().getFirst().seconds());
        // The live asset port scans inventory slots from zero and takes the first equal asset.
    }
}
