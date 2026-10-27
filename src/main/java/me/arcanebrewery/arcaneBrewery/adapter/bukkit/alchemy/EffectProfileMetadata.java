package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
/** Separate versioned metadata; absence preserves the old single-effect consumption path. */
public final class EffectProfileMetadata {
    private final String namespace;private final EffectProfileCodec codec=new EffectProfileCodec();
    public EffectProfileMetadata(String namespace){this.namespace=namespace;}
    private NamespacedKey key(String name){return new NamespacedKey(namespace,name);}
    public void markNewOperation(ItemMeta meta){markGeneration(meta,2);}
    public void captureInfusion(ItemMeta meta){if(generation(meta)==0)markNewOperation(meta);}
    public void markGeneration(ItemMeta meta,int generation){
        if(generation<1||generation>2)throw new IllegalArgumentException("unsupported effect generation");
        meta.getPersistentDataContainer().set(key("batch_effect_generation"),PersistentDataType.INTEGER,generation);
    }
    public int generation(ItemMeta meta){
        var p=meta.getPersistentDataContainer();if(!p.has(key("batch_effect_generation")))return 0;
        Integer value=p.get(key("batch_effect_generation"),PersistentDataType.INTEGER);if(value==null||(value!=1&&value!=2))throw new IllegalArgumentException("unsupported effect generation");return value;
    }
    public boolean newOperation(ItemMeta meta){return generation(meta)>0;}
    public void write(ItemMeta meta,EffectProfile profile){var p=meta.getPersistentDataContainer();p.set(key("drink_effect_profile_format"),PersistentDataType.INTEGER,EffectProfile.VERSION);p.set(key("drink_effect_profile"),PersistentDataType.BYTE_ARRAY,codec.encode(profile));}
    public Optional<EffectProfile> read(ItemMeta meta){
        var p=meta.getPersistentDataContainer();if(!p.has(key("drink_effect_profile_format"))&&!p.has(key("drink_effect_profile")))return Optional.empty();
        Integer version=p.get(key("drink_effect_profile_format"),PersistentDataType.INTEGER);byte[] bytes=p.get(key("drink_effect_profile"),PersistentDataType.BYTE_ARRAY);
        if(version==null||version!=EffectProfile.VERSION||bytes==null)throw new IllegalArgumentException("incomplete effect profile");
        return Optional.of(codec.decode(bytes));
    }
}
