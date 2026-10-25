package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Captured equipment and realized reaction conditions, independent of formulation identity. */
public final class AlchemyBatchConditionsMetadata {
    private final String namespace;
    public AlchemyBatchConditionsMetadata(String namespace) { this.namespace=namespace; }
    private NamespacedKey key(String name) {return new NamespacedKey(namespace,name);}
    public void write(ItemMeta meta,AlchemyMachineCondition condition,AlchemyFailureAssessment result) {
        var pdc=meta.getPersistentDataContainer();
        pdc.set(key("batch_alchemy_stability"),PersistentDataType.DOUBLE,Math.max(0,Math.min(condition.integrity(),100-result.instability())));
        double previous=contamination(meta);
        pdc.set(key("batch_alchemy_contamination"),PersistentDataType.DOUBLE,Math.min(100,previous+condition.contamination()+result.contaminationGain()));
        pdc.set(key("batch_alchemy_outcome"),PersistentDataType.STRING,result.outcome().name());
    }
    private double read(ItemMeta meta,String name,double fallback) {
        var pdc=meta.getPersistentDataContainer();
        if(pdc.has(key(name)) && !pdc.has(key(name),PersistentDataType.DOUBLE)) throw new IllegalArgumentException("invalid alchemy condition type");
        double value=pdc.getOrDefault(key(name),PersistentDataType.DOUBLE,fallback);
        if(!Double.isFinite(value)||value<0||value>100) throw new IllegalArgumentException("invalid alchemy batch conditions");
        return value;
    }
    public double stability(ItemMeta meta) { return read(meta,"batch_alchemy_stability",100); }
    public double contamination(ItemMeta meta) { return read(meta,"batch_alchemy_contamination",0); }
}
