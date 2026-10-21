package me.arcanebrewery.arcaneBrewery.application.alchemy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AlchemyPreparationCodecTest {
    private final AlchemyPreparationCodec codec = new AlchemyPreparationCodec();

    @Test
    void roundTripPreservesCanonicalAdditives() {
        var source = new AlchemyPreparation(Map.of(
                "arcanebrewery:fire_catalyst", 1.0,
                "arcanebrewery:vision_catalyst", 2.0
        ));

        var decoded = codec.decodeBase64(codec.encodeBase64(source));

        assertEquals(source, decoded);
    }

    @Test
    void encodedFormIsDeterministicAcrossMapOrder() {
        var a = new AlchemyPreparation(Map.of(
                "arcanebrewery:fire_catalyst", 1.0,
                "arcanebrewery:vision_catalyst", 2.0
        ));
        var b = new AlchemyPreparation(new java.util.LinkedHashMap<>() {{
            put("arcanebrewery:vision_catalyst", 2.0);
            put("arcanebrewery:fire_catalyst", 1.0);
        }});

        assertEquals(codec.encodeBase64(a), codec.encodeBase64(b));
    }

    @Test
    void malformedPayloadIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> codec.decodeBase64("not-base64"));
    }
}
