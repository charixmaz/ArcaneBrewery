package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CaskHarvestMetadataTest {
    @Test void savedCheckpointSurvivesItemCopiesAndCannotBeReassignedOrReaged() {
        var metadata=new CaskHarvestMetadata("arcanebrewery"); var source=TestItems.water();
        var checkpoint=new CaskHarvestMetadata.Checkpoint(123_000,UUID.randomUUID(),"Brewer");
        source.edit(meta->metadata.write(meta,checkpoint));
        var recovered=source.clone(); assertEquals(checkpoint,metadata.read(recovered.getItemMeta()).orElseThrow());
        recovered.edit(meta->metadata.write(meta,checkpoint));
        assertThrows(IllegalArgumentException.class,()->recovered.edit(meta->metadata.write(meta,
                new CaskHarvestMetadata.Checkpoint(456_000,UUID.randomUUID(),"Other"))));
        assertEquals(checkpoint,metadata.read(recovered.getItemMeta()).orElseThrow());
    }
    @Test void oldBatchesHaveNoCheckpointAndIncompleteCheckpointsAreRejected() {
        var metadata=new CaskHarvestMetadata("arcanebrewery"); var item=TestItems.water();
        assertTrue(metadata.read(item.getItemMeta()).isEmpty());
        item.edit(meta->meta.getPersistentDataContainer().set(
                new NamespacedKey("arcanebrewery","cask_harvest_at"),PersistentDataType.LONG,123L));
        assertThrows(IllegalArgumentException.class,()->metadata.read(item.getItemMeta()));
    }
}

