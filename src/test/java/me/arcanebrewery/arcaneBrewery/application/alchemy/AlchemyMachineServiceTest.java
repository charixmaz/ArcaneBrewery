package me.arcanebrewery.arcaneBrewery.application.alchemy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AlchemyMachineServiceTest {
    private final AlchemyMachineService service = new AlchemyMachineService();

    @Test
    void combinesTwoDifferentCatalysts() {
        var fire = BuiltInAlchemyCatalysts.resolveExternal("minecraft:blaze_powder").orElseThrow();
        var veil = BuiltInAlchemyCatalysts.resolveExternal("minecraft:amethyst_shard").orElseThrow();

        var preparation = service.combine(List.of(fire, veil));

        assertEquals(2, preparation.additives().size());
        assertEquals(1.0, preparation.additives().get("arcanebrewery:fire_catalyst"));
        assertEquals(1.0, preparation.additives().get("arcanebrewery:invisibility_catalyst"));
    }

    @Test
    void duplicateCatalystsAccumulateDose() {
        var fire = BuiltInAlchemyCatalysts.resolveExternal("minecraft:blaze_powder").orElseThrow();

        var preparation = service.combine(List.of(fire, fire));

        assertEquals(1, preparation.additives().size());
        assertEquals(2.0, preparation.additives().get("arcanebrewery:fire_catalyst"));
    }

    @Test
    void diversityRaisesInstabilityPreview() {
        var fire = BuiltInAlchemyCatalysts.resolveExternal("minecraft:blaze_powder").orElseThrow();
        var veil = BuiltInAlchemyCatalysts.resolveExternal("minecraft:amethyst_shard").orElseThrow();

        assertTrue(service.previewInstability(List.of(fire, veil))
                > service.previewInstability(List.of(fire)));
    }

    @Test
    void processingUsesWallClockTime() {
        var state = service.start(1_000, 8_000, UUID.randomUUID());

        assertFalse(state.ready(8_999));
        assertTrue(state.ready(9_000));
        assertEquals(AlchemyMachineState.Status.COMPLETED, state.complete(9_000).status());
    }

    @Test
    void rejectsMoreThanTwoCatalysts() {
        var fire = BuiltInAlchemyCatalysts.resolveExternal("minecraft:blaze_powder").orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> service.combine(List.of(fire, fire, fire)));
    }

    @Test
    void timestampOverflowNeverBecomesReady() {
        var state = new AlchemyMachineState(
                AlchemyMachineState.VERSION,
                UUID.randomUUID(),
                Long.MAX_VALUE - 5,
                10,
                AlchemyMachineState.Status.PROCESSING
        );
        assertFalse(state.ready(Long.MAX_VALUE));
    }
}
