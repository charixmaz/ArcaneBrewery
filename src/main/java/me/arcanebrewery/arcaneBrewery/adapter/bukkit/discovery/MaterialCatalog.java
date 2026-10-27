package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.*;
/** Server-owned catalog projection. Identity comes from canonical metadata/registry, never display names. */
public final class MaterialCatalog {
    public record Entry(MaterialKey key,Material icon,String name,List<String> details) {public Entry {details=List.copyOf(details);}}
    private final IngredientItemAdapter items;
    private final Map<MaterialKey,Entry> entries;
    public MaterialCatalog(IngredientItemAdapter items) {
        this.items=Objects.requireNonNull(items);var data=new LinkedHashMap<MaterialKey,Entry>();
        for(var d:items.registry().definitions().values()) {
            Material icon=Material.matchMaterial(d.id());
            if(icon==null && !d.id().startsWith("minecraft:")) icon=Material.WHEAT;
            if(icon==null || (!WashableIngredients.MATERIALS.contains(icon))) continue;
            var key=new MaterialKey(MaterialKey.Kind.INGREDIENT,d.id());
            data.put(key,new Entry(key,icon,d.displayName(),List.of("Brewing ingredient; wash in a water cauldron.",
                "Essence Extractor: use actual quantities in either ingredient slot.",
                d.premium()?"Rare mature-Wheat harvest drop; canonical metadata identifies it.":"Obtain the actual material; renaming another item does not count.",
                me.arcanebrewery.arcaneBrewery.application.alchemy.IngredientAffinities.hint(d.id()),
                "Ratios blend base properties even when a catalyst selects the effect.",
                "Exact formulations and results require experimentation.")));
        }
        for(var d:ReagentRegistry.all()) {
            if(d.role()==ReagentRole.FUTURE_PURIFICATION) continue;
            var key=new MaterialKey(MaterialKey.Kind.CATALYST,d.id());
            data.put(key,new Entry(key,Objects.requireNonNull(Material.matchMaterial(d.externalId())),d.materialName(),List.of(
                "Role: "+d.role().name().replace('_',' ').toLowerCase(Locale.ROOT),
                "Use raw in the Arcane Infuser; catalyst washing is not required.",
                d.role()==ReagentRole.EFFECT_CATALYST?"An active catalyst; combine compatible families experimentally.":"Needs an effect catalyst in the other slot; this is a consumed modifier.",
                "One item per selected slot. Extra dose can increase instability.",
                d.risky()?"Deliberately risky transformation; inspect the resulting bottle.":"Inspect finished elixirs for their actual effects and safety.")));
        }
        for(var kind:me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.PreparedReagents.Kind.values()){
            var key=new MaterialKey(MaterialKey.Kind.CATALYST,"arcanebrewery:prepared_"+kind.name().toLowerCase(Locale.ROOT));
            data.put(key,new Entry(key,Material.PRISMARINE_SHARD,"Prepared "+kind.name().toLowerCase(Locale.ROOT)+" reagent",List.of("Made in the researched Reagent Processor.","Consumed by the matching specialist machine, not the starter infuser.","One operation per bottle per specialist type.")));
        }
        entries=Collections.unmodifiableMap(data);
    }
    public Map<MaterialKey,Entry> entries() {return entries;}
    public Set<MaterialKey> classify(ItemStack item) {
        if(DisplayOnlyItems.test(item) || item==null || (item.getType()==Material.AIR||item.getType()==Material.CAVE_AIR||item.getType()==Material.VOID_AIR) || item.getAmount()<=0) return Set.of();
        try{var prepared=me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.PreparedReagents.read(item);if(prepared.isPresent())return Set.of(new MaterialKey(MaterialKey.Kind.CATALYST,"arcanebrewery:prepared_"+prepared.get().name().toLowerCase(Locale.ROOT)));}catch(IllegalArgumentException bad){return Set.of();}
        var result=new HashSet<MaterialKey>();
        try {
            if(items.classify(item) instanceof IngredientInput.Ingredient ingredient) {
                var key=new MaterialKey(MaterialKey.Kind.INGREDIENT,ingredient.instance().ingredientId());
                if(entries.containsKey(key)) result.add(key);
            }
        } catch(IllegalArgumentException unsupported) { }
        ReagentRegistry.external(item.getType().getKey().toString()).ifPresent(d->{var key=new MaterialKey(MaterialKey.Kind.CATALYST,d.id());if(entries.containsKey(key))result.add(key);});
        return Set.copyOf(result);
    }
    public List<me.arcanebrewery.arcaneBrewery.guide.GuideCatalog.Entry> project(MaterialKey.Kind kind,Set<MaterialKey> known) {
        return entries.values().stream().filter(e->e.key().kind()==kind).map(e->known.contains(e.key())
            ?new me.arcanebrewery.arcaneBrewery.guide.GuideCatalog.Entry(e.icon(),e.name(),e.details())
            :new me.arcanebrewery.arcaneBrewery.guide.GuideCatalog.Entry(Material.GRAY_STAINED_GLASS_PANE,"???",List.of(MaterialRiddles.clue(e.key())))).toList();
    }
    public List<String> notification(Set<MaterialKey> unlocked) {
        var actual=unlocked.stream().filter(entries::containsKey).sorted(Comparator.comparing((MaterialKey k)->k.kind().name()).thenComparing(MaterialKey::registryId)).toList();
        if(actual.isEmpty())return List.of();
        if(actual.size()==1) {var e=entries.get(actual.getFirst());return List.of("§d✦ "+(e.key().kind()==MaterialKey.Kind.CATALYST?"Catalyst":"Ingredient")+" discovered: §f"+e.name()+"§r","§7Added to your Brewing Codex.§r");}
        var names=actual.stream().limit(3).map(k->entries.get(k).name()+" ("+k.kind().name().toLowerCase(Locale.ROOT)+")").toList();
        return List.of("§d✦ "+actual.size()+" material discoveries: §f"+String.join(", ",names)+(actual.size()>3?" +"+(actual.size()-3)+" more":"")+"§r","§7Added to your Brewing Codex.§r");
    }
}
