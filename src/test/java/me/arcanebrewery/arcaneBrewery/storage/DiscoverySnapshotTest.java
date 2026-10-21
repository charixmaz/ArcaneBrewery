package me.arcanebrewery.arcaneBrewery.storage;

import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static me.arcanebrewery.arcaneBrewery.storage.StorageFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscoverySnapshotTest {
    @Test
    void reconstitutionPreservesPresentationMetadataInsteadOfRegeneratingIt() {
        var original = discovery().rename(OWNER, "Canonical").shareFull(OWNER, FRIEND).snapshot();
        var historical = new DiscoverySnapshot(OWNER, original.fingerprint(), original.process(), TIME,
                new DrinkName("Historical Generator Name"), original.canonicalName(), original.visibility(), original.grants());
        var restored = PlayerDiscovery.restore(historical);
        assertEquals(historical, restored.snapshot());
        assertEquals("Historical Generator Name", restored.temporaryName().value());
        assertTrue(restored.renameUsed());
    }

    @Test
    void invalidReconstitutionCannotChangeIdentityOwnerKnowledgeOrVisibilityInvariants() {
        var s = discovery().snapshot();
        assertThrows(IllegalArgumentException.class, () -> new DiscoverySnapshot(OWNER,
                DrinkFingerprint.from(withFermentation(fermentation(23, 3600, 100, 0))), s.process(),
                TIME, s.temporaryName(), s.canonicalName(), s.visibility(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new DiscoverySnapshot(OWNER, s.fingerprint(), s.process(),
                TIME, s.temporaryName(), s.canonicalName(), DiscoveryVisibility.SHARED, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new DiscoverySnapshot(OWNER, s.fingerprint(), s.process(),
                TIME, s.temporaryName(), s.canonicalName(), DiscoveryVisibility.PRIVATE, Map.of(FRIEND, RecipeGrant.fullRecipe())));
        assertThrows(IllegalArgumentException.class, () -> new DiscoverySnapshot(OWNER, s.fingerprint(), s.process(),
                TIME, s.temporaryName(), s.canonicalName(), DiscoveryVisibility.SHARED, Map.of(OWNER, RecipeGrant.fullRecipe())));
        assertThrows(IllegalArgumentException.class, () -> new RecipeGrant(true, Set.of(ProcessField.AGING_WOOD)));
    }
}
