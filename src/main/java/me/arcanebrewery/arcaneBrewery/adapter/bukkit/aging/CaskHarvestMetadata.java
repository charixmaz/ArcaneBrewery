package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Saved with the original cask lane before attempting registration. Never age a retry again. */
public final class CaskHarvestMetadata {
    public record Checkpoint(long harvestedAt, UUID producer, String producerName) {
        public Checkpoint {
            Objects.requireNonNull(producer); Objects.requireNonNull(producerName);
            if(harvestedAt<0 || producerName.isBlank() || producerName.length()>64) throw new IllegalArgumentException("invalid harvest checkpoint");
        }
    }
    private final NamespacedKey time, producer, name;
    public CaskHarvestMetadata(String namespace) {
        time=new NamespacedKey(namespace,"cask_harvest_at"); producer=new NamespacedKey(namespace,"cask_harvest_producer");
        name=new NamespacedKey(namespace,"cask_harvest_name");
    }
    public Optional<Checkpoint> read(ItemMeta meta) {
        var data=meta.getPersistentDataContainer();
        if(!data.has(time) && !data.has(producer) && !data.has(name)) return Optional.empty();
        var at=data.get(time,PersistentDataType.LONG); var owner=data.get(producer,PersistentDataType.STRING); var label=data.get(name,PersistentDataType.STRING);
        if(at==null || owner==null || label==null) throw new IllegalArgumentException("incomplete harvest checkpoint");
        return Optional.of(new Checkpoint(at,UUID.fromString(owner),label));
    }
    public void write(ItemMeta meta, Checkpoint checkpoint) {
        var old=read(meta);
        if(old.isPresent() && !old.get().equals(checkpoint)) throw new IllegalArgumentException("harvest checkpoint cannot change");
        var data=meta.getPersistentDataContainer();
        data.set(time,PersistentDataType.LONG,checkpoint.harvestedAt());
        data.set(producer,PersistentDataType.STRING,checkpoint.producer().toString());
        data.set(name,PersistentDataType.STRING,checkpoint.producerName());
    }
}
