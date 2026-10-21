package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** ABAM V2: checksummed operation + fixed three native item blobs. No Java object deserialization. */
public final class AlchemyMachineSnapshotCodec {
    public static final int MAX_BYTES = 1_048_576;
    private static final int MAGIC = 0x4142414d;
    private final AlchemyPreparationCodec preparations = new AlchemyPreparationCodec();

    public byte[] encode(AlchemyMachineSnapshot snapshot) {
        try {
            var buffer = new ByteArrayOutputStream();
            var out = new DataOutputStream(buffer);
            out.writeInt(MAGIC); out.writeInt(2);
            out.writeBoolean(snapshot.operation().isPresent());
            if (snapshot.operation().isPresent()) {
                var operation = snapshot.operation().orElseThrow();
                var state = operation.state();
                uuid(out, state.batchId()); out.writeLong(state.startedAtEpochMillis()); out.writeLong(state.durationMillis());
                out.writeByte(state.status() == AlchemyMachineState.Status.PROCESSING ? 0 : 1);
                uuid(out, operation.sourceBatch());
                byte[] preparation = preparations.encodeBase64(operation.preparation()).getBytes(StandardCharsets.US_ASCII);
                out.writeInt(preparation.length); out.write(preparation);
            }
            for (byte[] item : snapshot.items()) { out.writeInt(item.length); out.write(item); }
            out.flush();
            byte[] body = buffer.toByteArray();
            if (body.length + 32 > MAX_BYTES) throw new IllegalArgumentException("machine snapshot too large");
            out.write(digest(body)); out.flush();
            return buffer.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public AlchemyMachineSnapshot decode(byte[] payload) {
        if (payload == null || payload.length < 53 || payload.length > MAX_BYTES)
            throw new IllegalArgumentException("invalid machine snapshot size");
        byte[] body = Arrays.copyOf(payload, payload.length - 32);
        if (!MessageDigest.isEqual(digest(body), Arrays.copyOfRange(payload, body.length, payload.length)))
            throw new IllegalArgumentException("machine snapshot checksum mismatch");
        try (var in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != MAGIC || in.readInt() != 2) throw new IllegalArgumentException("unsupported machine snapshot");
            int present = in.readUnsignedByte();
            if (present > 1) throw new IllegalArgumentException("invalid operation marker");
            Optional<AlchemyMachineOperation> operation = Optional.empty();
            if (present == 1) {
                UUID batch = uuid(in); long start = in.readLong(); long duration = in.readLong();
                int status = in.readUnsignedByte();
                if (status > 1) throw new IllegalArgumentException("invalid machine status");
                UUID source = uuid(in);
                byte[] prep = blob(in, 5464);
                if (java.util.stream.IntStream.range(0, prep.length).anyMatch(i -> prep[i] < 0))
                    throw new IllegalArgumentException("invalid preparation encoding");
                operation = Optional.of(new AlchemyMachineOperation(new AlchemyMachineState(1, batch, start, duration,
                        status == 0 ? AlchemyMachineState.Status.PROCESSING : AlchemyMachineState.Status.COMPLETED),
                        source, preparations.decodeBase64(new String(prep, StandardCharsets.US_ASCII))));
            }
            var items = List.of(blob(in, 262144), blob(in, 262144), blob(in, 262144));
            if (in.available() != 0) throw new IllegalArgumentException("trailing machine snapshot bytes");
            return new AlchemyMachineSnapshot(operation, items);
        } catch (IOException failure) { throw new IllegalArgumentException("truncated machine snapshot", failure); }
    }

    private byte[] blob(DataInputStream in, int limit) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > limit || length > in.available()) throw new IllegalArgumentException("invalid machine blob length");
        return in.readNBytes(length);
    }
    private void uuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private UUID uuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
