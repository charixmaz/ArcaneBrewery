package me.arcanebrewery.arcaneBrewery.adapter.bukkit;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElixirPresentationTest {
    @Test void refreshPreservesIdentityOpaqueMetadataQuantityAndCustomEffects() {
        var bottle=TestItems.water();
        bottle.edit(meta -> {
            var data=meta.getPersistentDataContainer();
            data.set(new NamespacedKey("arcanebrewery","drink_format"),PersistentDataType.INTEGER,2);
            data.set(new NamespacedKey("arcanebrewery","drink_stage"),PersistentDataType.STRING,"FINISHED");
            data.set(new NamespacedKey("arcanebrewery","drink_fingerprint"),PersistentDataType.STRING,"f".repeat(64));
            data.set(new NamespacedKey("arcanebrewery","drink_strength"),PersistentDataType.DOUBLE,42.5);
            data.set(new NamespacedKey("arcanebrewery","drink_quality"),PersistentDataType.DOUBLE,87.0);
            data.set(new NamespacedKey("arcanebrewery","drink_contamination"),PersistentDataType.DOUBLE,3.0);
            data.set(new NamespacedKey("arcanebrewery","drink_magical_potency"),PersistentDataType.DOUBLE,65.0);
            data.set(new NamespacedKey("otherplugin","opaque"),PersistentDataType.STRING,"preserved");
            meta.setDisplayName("§fUndermatured Cask Batch");
        });
        var before=bottle.getItemMeta(); var keys=before.getPersistentDataContainer().getKeys(); var profile=new FinalDrinkMetadata("arcanebrewery").readConsumption(before);
        var effects=((PotionMeta)before).getCustomEffects();
        var updated=bottle.getItemMeta(); assertTrue(ElixirPresentation.refresh(updated,new FinalDrinkMetadata("arcanebrewery")));
        bottle.setItemMeta(updated);
        assertEquals(keys,updated.getPersistentDataContainer().getKeys()); assertEquals(profile,new FinalDrinkMetadata("arcanebrewery").readConsumption(updated));
        assertEquals("f".repeat(64),updated.getPersistentDataContainer().get(new NamespacedKey("arcanebrewery","drink_fingerprint"),PersistentDataType.STRING));
        assertEquals("preserved",updated.getPersistentDataContainer().get(new NamespacedKey("otherplugin","opaque"),PersistentDataType.STRING));
        assertEquals(1,bottle.getAmount()); assertEquals(effects,((PotionMeta)updated).getCustomEffects());
        assertTrue(updated.hasItemFlag(ItemFlag.HIDE_ADDITIONAL_TOOLTIP));
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.FALSE,updated.displayName().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC));
        assertTrue(updated.lore().stream().allMatch(line -> line.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC)==net.kyori.adventure.text.format.TextDecoration.State.FALSE));
        assertFalse(updated.getLore().stream().anyMatch(line -> line.contains("fingerprint") || line.contains("Discovery Owner")));
        var again=updated.clone(); ElixirPresentation.refresh(again,new FinalDrinkMetadata("arcanebrewery"));
        assertEquals(updated.getLore(),again.getLore());
    }
    @Test void vanillaOrUnsupportedPotionMetadataIsNeverRewritten() {
        var meta=TestItems.water().getItemMeta(); assertFalse(ElixirPresentation.refresh(meta,new FinalDrinkMetadata("arcanebrewery")));
        assertFalse(meta.hasDisplayName());
    }
}
