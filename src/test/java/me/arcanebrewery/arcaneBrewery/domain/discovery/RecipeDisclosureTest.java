package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Set;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static me.arcanebrewery.arcaneBrewery.domain.discovery.DiscoveryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeDisclosureTest {
    @Test
    void partialProjectionContainsNoFullProcessOrUnrequestedFields() {
        var selection = EnumSet.of(ProcessField.FERMENTATION_TEMPERATURE);
        var disclosure = RecipeDisclosure.from(process(), selection);
        selection.add(ProcessField.INGREDIENT_RATIOS);
        assertEquals(Set.of(ProcessField.FERMENTATION_TEMPERATURE), disclosure.fields().keySet());
        assertEquals(new RecipeDisclosure.Decimal(22), disclosure.fields().get(ProcessField.FERMENTATION_TEMPERATURE));
        assertTrue(java.util.Arrays.stream(RecipeDisclosure.class.getDeclaredFields())
                .noneMatch(field -> field.getType().equals(ProcessProfile.class)));
        assertTrue(java.util.Arrays.stream(RecipeKnowledge.Partial.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(ProcessProfile.class)));
        assertTrue(java.util.Arrays.stream(DiscoverySummary.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(ProcessProfile.class)));
    }

    @Test
    void everySelectableFieldProducesOnlyItsExactValue() {
        for (ProcessField field : ProcessField.values()) {
            var disclosure = RecipeDisclosure.from(process(), Set.of(field));
            assertEquals(Set.of(field), disclosure.fields().keySet());
            assertNotNull(disclosure.fields().get(field));
        }
        assertEquals(new RecipeDisclosure.Cuts(10, 80, 10),
                RecipeDisclosure.from(process(), Set.of(ProcessField.DISTILLATION_CUTS))
                        .fields().get(ProcessField.DISTILLATION_CUTS));
        assertEquals(new RecipeDisclosure.Count(7200),
                RecipeDisclosure.from(process(), Set.of(ProcessField.AGING_DURATION))
                        .fields().get(ProcessField.AGING_DURATION));
    }

    @Test
    void mapsAndNestedAmountsAreImmutable() {
        var disclosure = RecipeDisclosure.from(process(), Set.of(ProcessField.INGREDIENT_RATIOS));
        assertThrows(UnsupportedOperationException.class, () -> disclosure.fields().clear());
        var amounts = assertInstanceOf(RecipeDisclosure.Amounts.class,
                disclosure.fields().get(ProcessField.INGREDIENT_RATIOS));
        assertEquals(process().ingredientRatios(), amounts.values());
        assertThrows(UnsupportedOperationException.class, () -> amounts.values().clear());
    }

    @Test
    void emptyOrCompletePartialSelectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> discovery().sharePartial(OWNER, FRIEND, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> discovery().sharePartial(OWNER, FRIEND,
                EnumSet.allOf(ProcessField.class)));
        assertThrows(NullPointerException.class, () -> RecipeDisclosure.from(process(), null));
    }

    @Test
    void fullKnowledgeRejectsMismatchedFingerprint() {
        var known = assertInstanceOf(RecipeKnowledge.Full.class, discovery().knowledgeFor(OWNER));
        assertThrows(IllegalArgumentException.class, () -> new RecipeKnowledge.Full(FRIEND, known.discovery(),
                withFermentation(fermentation(80, 3600, 100, 0))));
    }
}
