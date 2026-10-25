package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static me.arcanebrewery.arcaneBrewery.gameplay.GameplayV2Test.*;
class BottleProvenanceTest {
    @Test void authoritativeProducerControlsHeldRenameAndOnlyBylineIsItalic() {
        var data=new FinalDrinkMetadata("arcanebrewery");var producer=UUID.randomUUID();var meta=TestItems.water().getItemMeta();
        var pdc=meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey("arcanebrewery","drink_format"),PersistentDataType.INTEGER,2);
        pdc.set(new NamespacedKey("arcanebrewery","drink_stage"),PersistentDataType.STRING,"FINISHED");
        pdc.set(new NamespacedKey("arcanebrewery","drink_fingerprint"),PersistentDataType.STRING,"f".repeat(64));
        for(String key:new String[]{"drink_strength","drink_quality","drink_contamination","drink_magical_potency"}) pdc.set(new NamespacedKey("arcanebrewery",key),PersistentDataType.DOUBLE,50.0);
        assertFalse(data.mayRenameHeld(meta,producer));
        data.producer(meta,producer,"Licht",perfect());meta.setDisplayName("Emberveil");
        assertTrue(data.mayRenameHeld(meta,producer));assertFalse(data.mayRenameHeld(meta,UUID.randomUUID()));
        ElixirPresentation.refresh(meta,data);assertTrue(meta.getLore().getFirst().contains("Brewed by Licht"));
        var italic=net.kyori.adventure.text.format.TextDecoration.ITALIC;
        assertEquals(net.kyori.adventure.text.format.TextDecoration.State.TRUE,meta.lore().getFirst().decoration(italic));
        assertTrue(meta.lore().subList(1,meta.lore().size()).stream().allMatch(l->l.decoration(italic)==net.kyori.adventure.text.format.TextDecoration.State.FALSE));
        assertEquals(producer,data.producer(meta).orElseThrow());assertEquals("Licht",data.producerLabel(meta));
    }
    @Test void unsafeCachedNameUsesUuidAndLegacyMetadataIsNotAttributed() {
        var data=new FinalDrinkMetadata("arcanebrewery");var meta=TestItems.water().getItemMeta();var producer=UUID.randomUUID();
        assertTrue(data.producer(meta).isEmpty());data.producer(meta,producer,"§kBad",perfect());
        assertEquals(producer.toString(),data.producerLabel(meta));
    }
}
