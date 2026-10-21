package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

public final class AlchemyPreparationCodec {
    private static final int MAGIC = 0x41425052; // ABPR
    private static final int VERSION = 1;
    private static final int MAX_ENTRIES = 16;
    private static final int MAX_TEXT = 256;
    private static final int MAX_BYTES = 4096;

    public String encodeBase64(AlchemyPreparation preparation) {
        Objects.requireNonNull(preparation);
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(preparation.additives().size());
                for (var entry : preparation.additives().entrySet()) {
                    writeText(out, entry.getKey());
                    out.writeDouble(entry.getValue());
                }
            }
            byte[] raw = bytes.toByteArray();
            if (raw.length > MAX_BYTES) throw new IllegalArgumentException("alchemy preparation too large");
            return Base64.getEncoder().encodeToString(raw);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public AlchemyPreparation decodeBase64(String encoded) {
        Objects.requireNonNull(encoded);
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("invalid alchemy preparation encoding", malformed);
        }
        if (raw.length > MAX_BYTES) throw new IllegalArgumentException("alchemy preparation too large");

        try (var in = new DataInputStream(new ByteArrayInputStream(raw))) {
            if (in.readInt() != MAGIC) throw new IllegalArgumentException("invalid alchemy preparation magic");
            if (in.readInt() != VERSION) throw new IllegalArgumentException("unsupported alchemy preparation version");
            int count = in.readInt();
            if (count < 1 || count > MAX_ENTRIES) throw new IllegalArgumentException("invalid additive count");
            var additives = new TreeMap<String, Double>();
            for (int i = 0; i < count; i++) {
                String id = readText(in);
                double dose = in.readDouble();
                if (additives.putIfAbsent(id, dose) != null)
                    throw new IllegalArgumentException("duplicate additive");
            }
            if (in.read() != -1) throw new IllegalArgumentException("trailing alchemy preparation bytes");
            return new AlchemyPreparation(additives);
        } catch (EOFException truncated) {
            throw new IllegalArgumentException("truncated alchemy preparation", truncated);
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid alchemy preparation", failure);
        }
    }

    private void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_TEXT) throw new IllegalArgumentException("invalid text length");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private String readText(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > MAX_TEXT) throw new IllegalArgumentException("invalid text length");
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }
}
