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
        var storage=new PlayerManaMetadata("arcanebrewery");
        data.set(new NamespacedKey("arcanebrewery","player_intoxication"),PersistentDataType.DOUBLE,55.0);
        data.set(new NamespacedKey("arcanebrewery","player_intox_updated"),PersistentDataType.LONG,1000L);
        data.set(new NamespacedKey("arcanebrewery","player_dependence"),PersistentDataType.DOUBLE,95.0);
        data.set(new NamespacedKey("arcanebrewery","player_hangover"),PersistentDataType.DOUBLE,90.0);
        var imported=storage.read(player); assertEquals(55,imported.state().load()); assertEquals(0,imported.depletion());
        var updated=new PlayerConditionPolicy().recover(imported,61_000);
        storage.write(player,updated);
        assertEquals(updated,new PlayerManaMetadata("arcanebrewery").read(player));
        assertEquals(95,data.get(new NamespacedKey("arcanebrewery","player_dependence"),PersistentDataType.DOUBLE));
        assertEquals(90,data.get(new NamespacedKey("arcanebrewery","player_hangover"),PersistentDataType.DOUBLE));
    }
    @Test void malformedLegacyNumbersAreSafelyBoundedWithoutErasingRawData() {
        var data=TestItems.stack(org.bukkit.Material.PAPER,1).getItemMeta().getPersistentDataContainer();
        Player player=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a) -> data);
        var key=new NamespacedKey("arcanebrewery","player_intoxication"); data.set(key,PersistentDataType.DOUBLE,Double.NaN);
        var storage=new PlayerManaMetadata("arcanebrewery");
        assertEquals(0,storage.read(player).state().load());
        assertTrue(Double.isNaN(data.get(key,PersistentDataType.DOUBLE)));
    }
}
