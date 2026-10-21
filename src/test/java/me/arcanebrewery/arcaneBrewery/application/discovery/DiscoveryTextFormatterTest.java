package me.arcanebrewery.arcaneBrewery.application.discovery;

import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryTextFormatterTest {
    private final DiscoveryTextFormatter formatter = new DiscoveryTextFormatter();

    @Test
    void unknownDoesNotLeakRecipeData() {
        var lines = formatter.format(new RecipeKnowledge.Unknown(UUID.randomUUID()));
        assertEquals(List.of("Unknown recipe."), lines);
    }

    @Test
    void identifiedContainsMetadataOnly() {
        UUID player = UUID.randomUUID();
        ProcessProfile process = process();
        var summary = new DiscoverySummary(UUID.randomUUID(), DrinkFingerprint.from(process), Instant.EPOCH,
                new DrinkName("Known Spirit"), DiscoveryVisibility.PUBLIC);
        var lines = formatter.format(new RecipeKnowledge.Identified(player, summary));
        assertTrue(lines.stream().anyMatch(line -> line.equals("Knowledge: IDENTIFIED")));
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Ingredients:")));
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Aging wood:")));
    }

    @Test
    void fullKnowledgeContainsExactProcess() {
        UUID player = UUID.randomUUID();
        ProcessProfile process = process();
        var summary = new DiscoverySummary(player, DrinkFingerprint.from(process), Instant.EPOCH,
                new DrinkName("Owner Spirit"), DiscoveryVisibility.PRIVATE);
        var lines = formatter.format(new RecipeKnowledge.Full(player, summary, process));
        assertTrue(lines.stream().anyMatch(line -> line.contains("minecraft:wheat=1.00")));
        assertTrue(lines.stream().anyMatch(line -> line.equals("Aging wood: oak")));
        assertTrue(lines.stream().anyMatch(line -> line.equals("Distillation passes: 1")));
    }

    @Test
    void partialOnlyFormatsDisclosedFields() {
        UUID player = UUID.randomUUID();
        ProcessProfile process = process();
        var summary = new DiscoverySummary(UUID.randomUUID(), DrinkFingerprint.from(process), Instant.EPOCH,
                new DrinkName("Shared Spirit"), DiscoveryVisibility.SHARED);
        var disclosure = RecipeDisclosure.from(process,
                Set.of(ProcessField.AGING_WOOD, ProcessField.FERMENTATION_CATALYST));
        var lines = formatter.format(new RecipeKnowledge.Partial(player, summary, disclosure));
        assertTrue(lines.stream().anyMatch(line -> line.equals("aging wood: oak")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("wild_yeast")));
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Ingredients:")));
        assertFalse(lines.stream().anyMatch(line -> line.startsWith("Distillation cuts:")));
    }

    private static ProcessProfile process() {
        return new ProcessProfile(
                Map.of("minecraft:wheat", 1.0),
                "distillation",
                new FermentationProfile("arcanebrewery:wild_yeast", 22, 60, 85, 10),
                new DistillationProfile(10, 80, 10, 78, 1),
                new AgingProfile("oak", 120, 12, 18, 95),
                Map.of()
        );
    }
}
