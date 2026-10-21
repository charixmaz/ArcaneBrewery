package me.arcanebrewery.arcaneBrewery.application.fermentation;

import me.arcanebrewery.arcaneBrewery.application.distillery.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FermentationStateCodec {
    private static final int MAGIC = 0x41424653;
    public static final int MAX_BYTES = 32_768;

    public byte[] encode(FermentationBatchState state) {
        Objects.requireNonNull(state, "state");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(FermentationBatchState.VERSION);
            writeUuid(out, state.batchId());
            out.writeLong(state.startedAtEpochMillis());
            out.writeByte(state.status().ordinal());
            var settings = state.settings();
            writeText(out, settings.yeastOrCatalyst());
            out.writeDouble(settings.temperatureCelsius());
            out.writeLong(settings.durationSeconds());
            out.writeDouble(settings.sanitation());
            out.writeDouble(settings.oxygenExposure());
            writeDraft(out, state.distillery());
            out.flush();
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("fermentation state too large");
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("unable to encode fermentation state", e);
        }
    }

    public FermentationBatchState decode(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_BYTES)
            throw new IllegalArgumentException("invalid fermentation state payload");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid fermentation state header");
            int version = in.readInt();
            if (version != FermentationBatchState.VERSION)
                throw new IllegalArgumentException("unsupported fermentation state version");
            UUID batchId = readUuid(in);
            long startedAt = in.readLong();
            int ordinal = in.readUnsignedByte();
            if (ordinal >= FermentationBatchState.Status.values().length)
                throw new IllegalArgumentException("invalid fermentation status");
            var settings = new FermentationSettings(readText(in), in.readDouble(), in.readLong(),
                    in.readDouble(), in.readDouble());
            var draft = readDraft(in);
            if (in.available() != 0) throw new IllegalArgumentException("trailing fermentation state bytes");
            return new FermentationBatchState(version, batchId, draft, settings, startedAt,
                    FermentationBatchState.Status.values()[ordinal]);
        } catch (IOException e) {
            throw new IllegalArgumentException("malformed fermentation state", e);
        }
    }

    public String encodeBase64(FermentationBatchState state) {
        return Base64.getEncoder().encodeToString(encode(state));
    }

    public FermentationBatchState decodeBase64(String value) {
        try {
            return decode(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed fermentation state", e);
        }
    }

    private static void writeDraft(DataOutputStream out, DistilleryProcessDraft draft) throws IOException {
        writeText(out, draft.preparationMethod());
        var controls = draft.controls();
        out.writeDouble(controls.temperatureCelsius());
        out.writeDouble(controls.headsCutPercent());
        out.writeDouble(controls.heartsCutPercent());
        out.writeDouble(controls.tailsCutPercent());
        out.writeInt(controls.passes());
        out.writeInt(draft.ingredientRatios().size());
        for (var entry : draft.ingredientRatios().entrySet()) {
            writeText(out, entry.getKey());
            out.writeDouble(entry.getValue());
            out.writeDouble(draft.ingredientPurity().get(entry.getKey()));
        }
    }

    private static DistilleryProcessDraft readDraft(DataInputStream in) throws IOException {
        String preparation = readText(in);
        var controls = new DistillationControls(in.readDouble(), in.readDouble(), in.readDouble(),
                in.readDouble(), in.readInt());
        int count = in.readInt();
        if (count < 1 || count > 128) throw new IllegalArgumentException("invalid fermentation ingredient count");
        var ratios = new TreeMap<String, Double>();
        var purity = new TreeMap<String, Double>();
        for (int i = 0; i < count; i++) {
            String id = readText(in);
            if (ratios.putIfAbsent(id, in.readDouble()) != null)
                throw new IllegalArgumentException("duplicate fermentation ingredient id");
            purity.put(id, in.readDouble());
        }
        return new DistilleryProcessDraft(ratios, purity, preparation, controls);
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits());
        out.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024) throw new IllegalArgumentException("fermentation text too long");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readText(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > 1024 || length > in.available()) throw new IllegalArgumentException("invalid fermentation text");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
}
