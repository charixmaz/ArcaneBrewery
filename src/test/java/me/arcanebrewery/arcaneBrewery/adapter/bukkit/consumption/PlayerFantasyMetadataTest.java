package me.arcanebrewery.arcaneBrewery.adapter.bukkit.consumption;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.consumption.*;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class PlayerFantasyMetadataTest {
    @Test void fantasyImportAndSaveLeaveAllHistoricalStateUntouched() {
        var data=TestItems.stack(org.bukkit.Material.PAPER,1).getItemMeta().getPersistentDataContainer();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a) -> {
            if (m.getName().equals("getPersistentDataContainer")) return data;
            throw new UnsupportedOperationException(m.getName());
        });
        var storage=new PlayerIntoxicationMetadata("arcanebrewery");
        var historical=new RecoveryCondition(new IntoxicationState(55,85,20,95,1000),90,80,1000);
        storage.write(player,historical);
        var imported=storage.readFantasy(player); assertEquals(0,imported.state().tolerance()); assertEquals(0,imported.withdrawal()); assertEquals(0,imported.hangover());
        var updated=new PlayerConditionPolicy(false).recover(imported,61_000);
        storage.writeFantasy(player,updated);
        assertEquals(historical,storage.read(player));
        assertEquals(updated,new PlayerIntoxicationMetadata("arcanebrewery").readFantasy(player));
        assertEquals(95,data.get(new NamespacedKey("arcanebrewery","player_dependence"),PersistentDataType.DOUBLE));
    }
    @Test void malformedLegacyNumbersAreSafelyBoundedWithoutErasingRawData() {
        var data=TestItems.stack(org.bukkit.Material.PAPER,1).getItemMeta().getPersistentDataContainer();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a) -> data);
        var key=new NamespacedKey("arcanebrewery","player_intoxication"); data.set(key,PersistentDataType.DOUBLE,Double.NaN);
        var storage=new PlayerIntoxicationMetadata("arcanebrewery");
        assertEquals(0,storage.readFantasy(player).state().intoxication());
        assertTrue(Double.isNaN(data.get(key,PersistentDataType.DOUBLE)));
    }
}
