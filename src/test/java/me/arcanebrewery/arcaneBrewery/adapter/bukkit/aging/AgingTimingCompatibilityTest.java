package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.aging.*;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AgingTimingCompatibilityTest {
    private final AgingBatchMetadata metadata=new AgingBatchMetadata("arcanebrewery");
    @Test void existingV1BottlesRetainDefaultMaturityAfterConfigurationChange() {
        var meta=TestItems.water().getItemMeta();
        var state=new AgingBatchState(1,UUID.randomUUID(),new AgingSettings("oak",12,18,95),1000);
        metadata.write(meta,state);
        meta.getPersistentDataContainer().remove(new NamespacedKey("arcanebrewery","aging_maturation_seconds"));
        assertEquals(120,metadata.read(meta).orElseThrow().maturationSeconds());
        assertEquals(1000,metadata.read(meta).orElseThrow().startedAtEpochMillis());
    }
    @Test void newBatchRetainsItsCapturedDurationAcrossSerialization() {
        var meta=TestItems.water().getItemMeta();
        var state=new AgingBatchState(1,UUID.randomUUID(),new AgingSettings("oak",12,18,95),1000,240);
        metadata.write(meta,state);
        assertEquals(state,new AgingBatchMetadata("arcanebrewery").read(meta).orElseThrow());
        meta.getPersistentDataContainer().set(new NamespacedKey("arcanebrewery","aging_maturation_seconds"),PersistentDataType.LONG,0L);
        assertThrows(IllegalArgumentException.class,() -> metadata.read(meta));
    }
}
