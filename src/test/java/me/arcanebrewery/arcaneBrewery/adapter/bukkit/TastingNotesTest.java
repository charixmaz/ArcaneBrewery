package me.arcanebrewery.arcaneBrewery.adapter.bukkit;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TastingNotesTest {
 @Test void absentNotesStayAbsentAndCaptureDoesNotReroll(){var bottle=TestItems.water();var meta=bottle.getItemMeta();assertTrue(TastingNotes.read(meta).isEmpty());TastingNotes.capture(meta,123);var note=TastingNotes.read(meta);assertTrue(note.isPresent());TastingNotes.capture(meta,999);assertEquals(note,TastingNotes.read(meta));bottle.setItemMeta(meta);assertEquals(note,TastingNotes.read(bottle.clone().getItemMeta()));}
}
