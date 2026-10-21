package me.arcanebrewery.arcaneBrewery.storage.codec;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RecipeGrantCodecTest {
    private final RecipeGrantCodec codec = new RecipeGrantCodec();

    @Test
    void roundTripsFullAndPartialGrantsInDeterministicOrder() {
        var full = RecipeGrant.fullRecipe();
        var partial = RecipeGrant.partial(Set.of(ProcessField.PREPARATION_METHOD, ProcessField.AGING_WOOD));
        assertNull(codec.encode(full));
        assertEquals(full, codec.decode("FULL", null));
        assertEquals("1:AGING_WOOD,PREPARATION_METHOD", codec.encode(partial));
        assertEquals(partial, codec.decode("PARTIAL", codec.encode(partial)));
    }

    @Test
    void invalidOrUnsupportedSelectionsAreNotDiscarded() {
        assertThrows(UnsupportedStorageVersionException.class, () -> codec.decode("PARTIAL", "2:AGING_WOOD"));
        for (String invalid : new String[]{"1:", "1:NO_FIELD", "1:AGING_WOOD,AGING_WOOD",
                "1:PREPARATION_METHOD,AGING_WOOD", "1:" + "x".repeat(512)}) {
            assertThrows(CorruptRecordException.class, () -> codec.decode("PARTIAL", invalid));
        }
        assertThrows(CorruptRecordException.class, () -> codec.decode("FULL", "1:AGING_WOOD"));
        assertThrows(CorruptRecordException.class, () -> codec.decode("BOGUS", null));
    }
}
