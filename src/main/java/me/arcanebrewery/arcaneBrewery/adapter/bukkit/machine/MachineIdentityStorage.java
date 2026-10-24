package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.*;
import java.util.*;

/** Chunk PDC works for every core and does not assume BlockState is TileState. */
public final class MachineIdentityStorage {
    private final String namespace;
    public MachineIdentityStorage(String namespace) { this.namespace = namespace; }
    public NamespacedKey key(Block core) {
        return new NamespacedKey(namespace,"machine_owner_" + (core.getX() & 15) + "_" + core.getY() + "_" + (core.getZ() & 15));
    }
    public Optional<MachineIdentity> read(Block core) {
        loaded(core);
        return read(core.getChunk().getPersistentDataContainer(),key(core));
    }
    public Optional<MachineIdentity> read(PersistentDataContainer data, NamespacedKey key) {
        if (!data.has(key)) return Optional.empty();
        String raw = data.get(key,PersistentDataType.STRING);
        if (raw == null || raw.length() > 256) throw new IllegalArgumentException("invalid machine ownership data");
        String[] fields = raw.split("\\|",-1);
        if (fields.length != 5 || !fields[0].equals("1")) throw new IllegalArgumentException("unknown machine ownership format");
        return Optional.of(new MachineIdentity(UUID.fromString(fields[1]),MachineIdentity.Kind.valueOf(fields[2]),
                UUID.fromString(fields[3]),fields[4]));
    }
    public void write(PersistentDataContainer data, NamespacedKey key, MachineIdentity identity) {
        if (identity.ownerName().contains("|")) throw new IllegalArgumentException("invalid cached owner name");
        data.set(key,PersistentDataType.STRING,"1|" + identity.machineId() + "|" + identity.kind() + "|" + identity.ownerId() + "|" + identity.ownerName());
    }
    public void write(Block core, MachineIdentity identity) { loaded(core); write(core.getChunk().getPersistentDataContainer(),key(core),identity); }
    public void remove(Block core) { loaded(core); core.getChunk().getPersistentDataContainer().remove(key(core)); }
    private void loaded(Block core) {
        if (!core.getWorld().isChunkLoaded(core.getX() >> 4,core.getZ() >> 4)) throw new IllegalStateException("machine chunk unloaded");
    }
}
