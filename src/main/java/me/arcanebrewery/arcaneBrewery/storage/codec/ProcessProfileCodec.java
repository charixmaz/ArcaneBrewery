package me.arcanebrewery.arcaneBrewery.storage.codec;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.process.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/** ABPC format V1: fixed field order, big-endian numbers and length-prefixed UTF-8. No class names. */
public final class ProcessProfileCodec {
    public static final int VERSION = 1;
    public static final int MAX_PAYLOAD_BYTES = 1_048_576;
    private static final int MAGIC = 0x41425043;
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_TEXT_BYTES = 4096;

    public byte[] encode(ProcessProfile process) {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            amounts(out, process.ingredientRatios());
            text(out, process.preparationMethod());
            var f = process.fermentation();
            text(out, f.yeastOrCatalyst());
            out.writeDouble(f.temperatureCelsius()); out.writeLong(f.durationSeconds());
            out.writeDouble(f.sanitation()); out.writeDouble(f.oxygenExposure());
            var d = process.distillation();
            out.writeDouble(d.headsCutPercent()); out.writeDouble(d.heartsCutPercent());
            out.writeDouble(d.tailsCutPercent()); out.writeDouble(d.targetTemperatureCelsius());
            out.writeInt(d.passes());
            var a = process.aging();
            text(out, a.woodType()); out.writeLong(a.durationSeconds());
            out.writeDouble(a.oxidation()); out.writeDouble(a.storageTemperatureCelsius());
            out.writeDouble(a.caskCleanliness());
            amounts(out, process.alchemicalAdditives());
            out.flush();
            if (bytes.size() > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("process payload too large");
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new StorageException("Unable to encode process", e);
        }
    }

    public ProcessProfile decode(byte[] payload) {
        if (payload == null || payload.length > MAX_PAYLOAD_BYTES)
            throw new CorruptRecordException("Invalid process payload size");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != MAGIC) throw new CorruptRecordException("Invalid process payload header");
            if (in.readInt() != VERSION) throw new UnsupportedStorageVersionException("Unsupported process codec version");
            Map<String, Double> ratios = amounts(in);
            String preparation = text(in);
            var f = new FermentationProfile(text(in), in.readDouble(), in.readLong(), in.readDouble(), in.readDouble());
            var d = new DistillationProfile(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readInt());
            var a = new AgingProfile(text(in), in.readLong(), in.readDouble(), in.readDouble(), in.readDouble());
            var process = ProcessProfile.restoreCanonical(ratios, preparation, f, d, a, amounts(in));
            if (in.available() != 0 || !Arrays.equals(payload, encode(process)))
                throw new CorruptRecordException("Noncanonical process payload");
            return process;
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            throw new CorruptRecordException("Malformed process payload", e);
        }
    }

    private void amounts(DataOutputStream out, Map<String, Double> values) throws IOException {
        if (values.size() > MAX_ENTRIES) throw new IllegalArgumentException("too many process entries");
        out.writeInt(values.size());
        for (var entry : new TreeMap<>(values).entrySet()) {
            text(out, entry.getKey()); out.writeDouble(entry.getValue());
        }
    }

    private Map<String, Double> amounts(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_ENTRIES) throw new CorruptRecordException("Invalid process entry count");
        var values = new TreeMap<String, Double>();
        for (int i = 0; i < count; i++) {
            if (values.putIfAbsent(text(in), in.readDouble()) != null)
                throw new CorruptRecordException("Duplicate process entry");
        }
        return values;
    }

    private void text(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TEXT_BYTES) throw new IllegalArgumentException("process text too long");
        out.writeInt(bytes.length); out.write(bytes);
    }

    private String text(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_TEXT_BYTES || length > in.available())
            throw new CorruptRecordException("Invalid process text length");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
    }
}
