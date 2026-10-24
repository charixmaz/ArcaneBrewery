package me.arcanebrewery.arcaneBrewery.application.machine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Versioned, bounded atomic envelope: process state and real slot payloads. */
public final class MachineInventoryCodec {
    public record Snapshot(String process, List<byte[]> slots) {
        public Snapshot {
            Objects.requireNonNull(process);
            if (process.chars().anyMatch(value -> value>127)) throw new IllegalArgumentException("non-ASCII process state");
            if (process.length() > 100_000 || slots.size() != 9) throw new IllegalArgumentException("invalid machine snapshot");
            slots = slots.stream().map(bytes -> {
                if (bytes.length > 262_144) throw new IllegalArgumentException("item payload too large");
                return bytes.clone();
            }).toList();
        }
        @Override public List<byte[]> slots() { return slots.stream().map(byte[]::clone).toList(); }
    }
    public byte[] encode(Snapshot snapshot) {
        try {
            var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
            out.writeInt(0x41424d31); byte[] state = snapshot.process().getBytes(StandardCharsets.UTF_8);
            if (state.length > 100_000) throw new IllegalArgumentException("process too large");
            out.writeInt(state.length); out.write(state);
            for (byte[] item : snapshot.slots()) { out.writeInt(item.length); out.write(item); }
            out.flush(); byte[] body = bytes.toByteArray();
            if (body.length > 1_048_576) throw new IllegalArgumentException("machine snapshot too large");
            out.write(hash(body)); return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    public Snapshot decode(byte[] raw) {
        if (raw.length < 76 || raw.length > 1_048_608) throw new IllegalArgumentException("invalid machine envelope size");
        byte[] body = Arrays.copyOf(raw,raw.length - 32);
        if (!MessageDigest.isEqual(hash(body),Arrays.copyOfRange(raw,body.length,raw.length))) throw new IllegalArgumentException("corrupt machine snapshot");
        try (var in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != 0x41424d31) throw new IllegalArgumentException("unknown machine snapshot");
            byte[] state = bytes(in,100_000);
            for (byte value : state) if (value < 0) throw new IllegalArgumentException("non-ASCII process state");
            var items = new ArrayList<byte[]>();
            for (int i = 0; i < 9; i++) items.add(bytes(in,262_144));
            if (in.available() != 0) throw new IllegalArgumentException("trailing machine data");
            return new Snapshot(new String(state,StandardCharsets.UTF_8),items);
        } catch (IOException failure) { throw new IllegalArgumentException("truncated machine snapshot",failure); }
    }
    private byte[] bytes(DataInputStream in,int max) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > max || length > in.available()) throw new IllegalArgumentException("invalid machine field length");
        return in.readNBytes(length);
    }
    private byte[] hash(byte[] body) {
        try { return MessageDigest.getInstance("SHA-256").digest(body); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
