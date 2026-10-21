package me.arcanebrewery.arcaneBrewery.storage.codec;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import static me.arcanebrewery.arcaneBrewery.domain.SimulationFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ProcessProfileCodecTest {
    private final ProcessProfileCodec codec = new ProcessProfileCodec();

    @Test
    void exactRoundTripIncludesAllProcessAndBatchFields() {
        var p = process();
        assertEquals(p, codec.decode(codec.encode(p)));
        assertEquals(DrinkFingerprint.from(p), DrinkFingerprint.from(codec.decode(codec.encode(p))));
        assertArrayEquals(codec.encode(p), codec.encode(codec.decode(codec.encode(p))));
        assertEquals(0x41425043, ByteBuffer.wrap(codec.encode(p)).getInt());
        assertEquals(1, ByteBuffer.wrap(codec.encode(p)).getInt(4));
    }

    @Test
    void floatingPointRatiosRemainBitExactAcrossRepeatedRoundTrips() {
        var random = new SplittableRandom(12345);
        var base = process();
        for (int trial = 0; trial < 500; trial++) {
            Map<String, Double> ratios = new TreeMap<>();
            for (int ingredient = 0; ingredient < 7; ingredient++)
                ratios.put("custom:grain_" + ingredient, random.nextDouble(0.001, 10000));
            var original = new ProcessProfile(ratios, "mash", base.fermentation(), base.distillation(),
                    base.aging(), base.alchemicalAdditives());
            var decoded = codec.decode(codec.encode(original));
            assertEquals(original, decoded);
            assertEquals(DrinkFingerprint.from(original), DrinkFingerprint.from(decoded));
            assertEquals(original, codec.decode(codec.encode(decoded)));
        }
    }

    @Test
    void mapOrderingAndCustomUnicodeIdentifiersEncodeDeterministically() {
        var p = process();
        var custom = new ProcessProfile(Map.of("mod:月莓';--", 2.0, "mod:grain", 1.0), "steep",
                p.fermentation(), p.distillation(), p.aging(), Map.of("mod:星塵", 0.3333333333333333));
        assertEquals(custom, codec.decode(codec.encode(custom)));
        var map = new java.util.LinkedHashMap<String, Double>();
        map.put("mod:grain", 1.0); map.put("mod:月莓';--", 2.0);
        assertArrayEquals(codec.encode(custom), codec.encode(new ProcessProfile(map, "steep",
                p.fermentation(), p.distillation(), p.aging(), custom.alchemicalAdditives())));
    }

    @Test
    void unsupportedVersionAndMalformedPayloadsFailExplicitly() {
        byte[] bytes = codec.encode(process());
        byte[] future = bytes.clone();
        ByteBuffer.wrap(future).putInt(4, 999);
        assertThrows(UnsupportedStorageVersionException.class, () -> codec.decode(future));
        assertThrows(CorruptRecordException.class, () -> codec.decode(null));
        assertThrows(CorruptRecordException.class, () -> codec.decode(new byte[0]));
        assertThrows(CorruptRecordException.class, () -> codec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(CorruptRecordException.class, () -> codec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        byte[] excessive = bytes.clone();
        ByteBuffer.wrap(excessive).putInt(8, Integer.MAX_VALUE);
        assertThrows(CorruptRecordException.class, () -> codec.decode(excessive));
        assertThrows(CorruptRecordException.class, () -> codec.decode(
                new byte[ProcessProfileCodec.MAX_PAYLOAD_BYTES + 1]));
    }

    @Test
    void nonFiniteRatiosAndInvalidUtf8AreRejected() {
        byte[] bytes = codec.encode(process());
        // First sorted ingredient is honey; its ratio follows header, count, length and five UTF-8 bytes.
        ByteBuffer.wrap(bytes).putDouble(21, Double.NaN);
        assertThrows(CorruptRecordException.class, () -> codec.decode(bytes));
        byte[] badText = codec.encode(process());
        badText[16] = (byte) 0xff;
        assertThrows(CorruptRecordException.class, () -> codec.decode(badText));
    }

    @Test
    void encodeBoundsAndCanonicalRestoreValidationAreEnforced() {
        var p = process();
        var longId = new ProcessProfile(Map.of("x".repeat(4097), 1.0), "mash", p.fermentation(),
                p.distillation(), p.aging(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(longId));
        assertThrows(IllegalArgumentException.class, () -> ProcessProfile.restoreCanonical(
                Map.of("wheat", 2.0), "mash", p.fermentation(), p.distillation(), p.aging(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ProcessProfile.restoreCanonical(
                Map.of(" WHEAT ", 1.0), "mash", p.fermentation(), p.distillation(), p.aging(), Map.of()));
    }
}
