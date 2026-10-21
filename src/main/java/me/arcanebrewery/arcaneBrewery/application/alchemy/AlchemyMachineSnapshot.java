package me.arcanebrewery.arcaneBrewery.application.alchemy;

import java.util.*;

/** One atomic machine record: operation and three bounded opaque native-item payloads. */
public final class AlchemyMachineSnapshot {
    private final Optional<AlchemyMachineOperation> operation;
    private final List<byte[]> items;
    public AlchemyMachineSnapshot(Optional<AlchemyMachineOperation> operation, List<byte[]> items) {
        this.operation = Objects.requireNonNull(operation);
        if (items.size() != 3) throw new IllegalArgumentException("machine must have three item slots");
        this.items = items.stream().map(bytes -> {
            if (bytes.length > 262144) throw new IllegalArgumentException("machine item too large");
            return bytes.clone();
        }).toList();
    }
    public Optional<AlchemyMachineOperation> operation() { return operation; }
    public List<byte[]> items() { return items.stream().map(byte[]::clone).toList(); }
}
