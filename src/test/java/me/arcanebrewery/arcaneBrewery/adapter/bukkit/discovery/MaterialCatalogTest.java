package me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.ingredient.*;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MaterialCatalogTest {
    private final IngredientItemAdapter items=new IngredientItemAdapter(BuiltInIngredients.registry(),new IngredientMetadataCodec("arcanebrewery"));
    private final MaterialCatalog catalog=new MaterialCatalog(items);
    @Test void obtainableCatalogHasEightIngredientsAndTwentySixReagentsWithoutUselessEntries() {
        assertEquals(8,catalog.entries().keySet().stream().filter(k->k.kind()==MaterialKey.Kind.INGREDIENT).count());
        assertEquals(29,catalog.entries().keySet().stream().filter(k->k.kind()==MaterialKey.Kind.CATALYST).count());
        assertEquals(25,items.registry().definitions().size());
    }
    @Test void unknownPagesContainNoNamesIconsPropertiesOrFormulations() {
        for(var kind:MaterialKey.Kind.values())for(var entry:catalog.project(kind,Set.of())) {
            assertEquals("???",entry.title());assertEquals(Material.GRAY_STAINED_GLASS_PANE,entry.icon());
            assertFalse(entry.lines().toString().contains("Blaze"));assertFalse(entry.lines().toString().contains("amplifier"));assertFalse(entry.lines().toString().contains("Strength"));
        }
    }
    @Test void actualCanonicalCustomIngredientIdentityWinsOverRenamingAndQuantity() {
        var raw=TestItems.stack(Material.WHEAT,1);raw.edit(m->m.setDisplayName("Exclusive Brewer's Grain"));
        var ordinary=catalog.classify(raw);assertEquals(Set.of(new MaterialKey(MaterialKey.Kind.INGREDIENT,"minecraft:wheat")),ordinary);
        var definition=items.registry().require(BuiltInIngredients.PREMIUM_GRAIN);
        var custom=items.write(raw,new IngredientInstance(definition.id(),0,1,IngredientModifiers.NONE,Optional.empty()),false);
        assertEquals(Set.of(new MaterialKey(MaterialKey.Kind.INGREDIENT,definition.id())),catalog.classify(custom));
        raw.setAmount(64);assertEquals(ordinary,catalog.classify(raw));
    }
    @Test void oneItemCanUnlockBothRolesAndOnlyDiscoveredEntryRevealsItsBroadUse() {
        var keys=catalog.classify(TestItems.stack(Material.GOLDEN_APPLE,1));assertEquals(2,keys.size());
        var catalyst=catalog.project(MaterialKey.Kind.CATALYST,keys).stream().filter(e->!e.title().equals("???")).toList();
        assertEquals(1,catalyst.size());assertEquals("Golden Apple",catalyst.getFirst().title());assertTrue(catalyst.getFirst().lines().toString().contains("Arcane Infuser"));
        assertFalse(catalyst.getFirst().lines().toString().contains("ABSORPTION"));
    }
    @Test void virtualGuideAndMachineItemsCannotBecomeDiscoveryEvidence() {
        var icon=TestItems.stack(Material.BLAZE_ROD,1);icon.edit(DisplayOnlyItems::mark);assertTrue(catalog.classify(icon).isEmpty());
        assertEquals(1,catalog.classify(TestItems.stack(Material.BLAZE_ROD,1)).size());
    }
    @Test void batchedNotificationIsCompactAndDuplicateInputsCannotMultiplyIt() {
        var keys=new HashSet<MaterialKey>();keys.addAll(catalog.classify(TestItems.stack(Material.BLAZE_ROD,64)));keys.addAll(catalog.classify(TestItems.stack(Material.BLAZE_ROD,1)));
        assertEquals(2,catalog.notification(keys).size());assertTrue(catalog.notification(keys).getFirst().contains("Catalyst discovered: §fBlaze Rod"));
        assertEquals(2,catalog.notification(catalog.entries().keySet()).size());assertTrue(catalog.notification(catalog.entries().keySet()).getFirst().contains("more"));
        assertTrue(catalog.notification(Set.of()).isEmpty());
    }
    @Test void everyObtainableLockedEntryHasADistinctAcquisitionClueAndPreparedItemsUseCanonicalIds(){
        var clues=catalog.entries().keySet().stream().map(MaterialRiddles::clue).toList();assertEquals(37,clues.size());assertEquals(37,new HashSet<>(clues).size());assertTrue(clues.stream().noneMatch(c->c.contains("registered material")));
        var prepared=me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.PreparedReagents.create(me.arcanebrewery.arcaneBrewery.adapter.bukkit.research.PreparedReagents.Kind.DURATION,m->TestItems.stack(m,1));
        assertEquals(Set.of(new MaterialKey(MaterialKey.Kind.CATALYST,"arcanebrewery:prepared_duration")),catalog.classify(prepared));
    }
}
