package me.arcanebrewery.arcaneBrewery.application.distillery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DistilleryStateCodec {
    private static final int MAGIC = 0x41424453;
    public static final int MAX_BYTES = 32_768;

    public byte[] encode(DistilleryBatchState state) {
        Objects.requireNonNull(state, "state");
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(DistilleryBatchState.VERSION);
            writeUuid(out, state.batchId());
            writeUuid(out, state.machine().worldId());
            out.writeInt(state.machine().x()); out.writeInt(state.machine().y()); out.writeInt(state.machine().z());
            out.writeLong(state.startedAtEpochMillis());
            out.writeLong(state.durationMillis());
            out.writeByte(state.status().ordinal());
            writeText(out, state.draft().preparationMethod());
            var c = state.draft().controls();
            out.writeDouble(c.temperatureCelsius());
            out.writeDouble(c.headsCutPercent());
            out.writeDouble(c.heartsCutPercent());
            out.writeDouble(c.tailsCutPercent());
            out.writeInt(c.passes());
            out.writeInt(state.draft().ingredientRatios().size());
            for (var e : state.draft().ingredientRatios().entrySet()) {
                writeText(out, e.getKey());
                out.writeDouble(e.getValue());
                out.writeDouble(state.draft().ingredientPurity().get(e.getKey()));
            }
            out.flush();
            if (bytes.size() > MAX_BYTES) throw new IllegalArgumentException("distillery state too large");
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("unable to encode distillery state", e);
        }
    }

    public DistilleryBatchState decode(byte[] payload) {
        if (payload == null || payload.length == 0 || payload.length > MAX_BYTES)
            throw new IllegalArgumentException("invalid distillery state payload");
        try {
            var in = new DataInputStream(new ByteArrayInputStream(payload));
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid distillery state header");
            int version = in.readInt();
            if (version != DistilleryBatchState.VERSION) throw new IllegalArgumentException("unsupported distillery state version");
            UUID batch = readUuid(in);
            MachineLocation machine = new MachineLocation(readUuid(in), in.readInt(), in.readInt(), in.readInt());
            long started = in.readLong();
            long duration = in.readLong();
            int statusOrdinal = in.readUnsignedByte();
            if (statusOrdinal >= DistilleryBatchState.Status.values().length) throw new IllegalArgumentException("invalid distillery status");
            String preparation = readText(in);
            var controls = new DistillationControls(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readInt());
            int count = in.readInt();
            if (count < 1 || count > 128) throw new IllegalArgumentException("invalid ingredient count");
            var ratios = new TreeMap<String, Double>();
            var purity = new TreeMap<String, Double>();
            for (int i = 0; i < count; i++) {
                String id = readText(in);
                if (ratios.putIfAbsent(id, in.readDouble()) != null) throw new IllegalArgumentException("duplicate ingredient id");
                purity.put(id, in.readDouble());
            }
            if (in.available() != 0) throw new IllegalArgumentException("trailing distillery state bytes");
            return new DistilleryBatchState(version, batch, machine,
                    new DistilleryProcessDraft(ratios, purity, preparation, controls),
                    started, duration, DistilleryBatchState.Status.values()[statusOrdinal]);
        } catch (IOException e) {
            throw new IllegalArgumentException("malformed distillery state", e);
        }
    }

    public String encodeBase64(DistilleryBatchState state) {
        return Base64.getEncoder().encodeToString(encode(state));
    }

    public DistilleryBatchState decodeBase64(String value) {
        try {
            return decode(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("malformed distillery state", e);
        }
    }

    private static void writeUuid(DataOutputStream out, UUID id) throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }
    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024) throw new IllegalArgumentException("distillery text too long");
        out.writeShort(bytes.length); out.write(bytes);
    }
    private static String readText(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > 1024 || length > in.available()) throw new IllegalArgumentException("invalid distillery text");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
}
