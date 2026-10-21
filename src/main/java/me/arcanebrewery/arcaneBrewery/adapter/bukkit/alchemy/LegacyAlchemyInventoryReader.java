package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import java.io.*;
import java.util.*;
import java.util.function.BiFunction;

/** One-time, bounded allowlisted import only. No arbitrary ConfigurationSerializable aliases are executed. */
public final class LegacyAlchemyInventoryReader {
    private static final int MAX_BYTES = 1_048_576;
    private static final Set<String> ALLOWED = Set.of("org.bukkit.util.io.Wrapper", "java.util.HashMap",
            "java.util.LinkedHashMap", "java.util.ArrayList", "java.util.CollSer", "java.util.Map$Entry",
            "java.lang.Object", "java.lang.String", "java.lang.Number", "java.lang.Integer",
            "java.lang.Long", "java.lang.Double", "java.lang.Float", "java.lang.Short", "java.lang.Byte",
            "java.lang.Boolean", "java.lang.Character", "com.google.common.collect.ImmutableMap$SerializedForm",
            "com.google.common.collect.RegularImmutableMap", "com.google.common.collect.ImmutableMap",
            "com.google.common.collect.SingletonImmutableBiMap", "com.google.common.collect.ImmutableBiMap",
            "java.util.ImmutableCollections$Map1", "java.util.ImmutableCollections$MapN");
    private final BiFunction<String, Map<String, Object>, Object> decode;

    public LegacyAlchemyInventoryReader() { this(LegacyAlchemyInventoryReader::builtin); }
    public LegacyAlchemyInventoryReader(BiFunction<String, Map<String, Object>, Object> decode) {
        this.decode = Objects.requireNonNull(decode);
    }

    public ItemStack[] read(String encoded) {
        if (encoded == null || encoded.length() > ((MAX_BYTES + 2) / 3) * 4)
            throw new IllegalArgumentException("legacy inventory exceeds limit");
        byte[] raw = Base64.getDecoder().decode(encoded);
        if (raw.length > MAX_BYTES) throw new IllegalArgumentException("legacy inventory exceeds limit");
        try (var in = new SafeInput(new ByteArrayInputStream(raw))) {
            if (in.readInt() != 3) throw new IllegalArgumentException("invalid legacy inventory slot count");
            var result = new ItemStack[3];
            for (int i = 0; i < 3; i++) {
                Object item = materialize(in.readObject(), 0);
                if (item != null && !(item instanceof ItemStack)) throw new IllegalArgumentException("invalid legacy item");
                result[i] = (ItemStack) item;
            }
            try {
                in.readObject();
                throw new IllegalArgumentException("trailing legacy inventory data");
            } catch (EOFException expected) { /* exactly three objects */ }
            return result;
        } catch (IOException | ClassNotFoundException failure) {
            throw new IllegalArgumentException("legacy inventory could not be safely imported", failure);
        }
    }

    private final class SafeInput extends ObjectInputStream {
        SafeInput(InputStream stream) throws IOException {
            super(stream);
            enableResolveObject(true);
            setObjectInputFilter(info -> {
                if (info.depth() > 32 || info.references() > 10000 || info.streamBytes() > MAX_BYTES
                        || info.arrayLength() > 262144) return ObjectInputFilter.Status.REJECTED;
                Class<?> type = info.serialClass();
                if (type == null) return ObjectInputFilter.Status.UNDECIDED;
                while (type.isArray()) type = type.getComponentType();
                return type.isPrimitive() || type == SerializedItem.class || ALLOWED.contains(type.getName())
                        ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
            });
        }
        @Override protected Object resolveObject(Object value) throws IOException {
            if (value == null || !value.getClass().getName().equals("org.bukkit.util.io.Wrapper")) return value;
            try {
                var field = value.getClass().getDeclaredField("map");
                field.setAccessible(true);
                if (!(field.get(value) instanceof Map<?, ?> raw) || raw.size() > 4096)
                    throw new InvalidObjectException("invalid serialized item map");
                var map = new HashMap<String, Object>();
                for (var entry : raw.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) throw new InvalidObjectException("invalid item map key");
                    map.put(key, entry.getValue());
                }
                Object alias = map.get("==");
                if (!"ItemStack".equals(alias) && !"ItemMeta".equals(alias))
                    throw new InvalidObjectException("unsupported serialized item alias");
                return new SerializedItem((String) alias, map);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                throw new InvalidObjectException("unsupported legacy item representation");
            }
        }
    }

    private record SerializedItem(String alias, Map<String, Object> values) { }

    private Object materialize(Object value, int depth) {
        if (depth > 32) throw new IllegalArgumentException("recursive legacy item data");
        if (value instanceof SerializedItem item) {
            @SuppressWarnings("unchecked")
            var values = (Map<String, Object>) materialize(item.values(), depth + 1);
            return decode.apply(item.alias(), values);
        }
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<Object, Object>();
            map.forEach((key, entry) -> result.put(key, materialize(entry, depth + 1)));
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(entry -> materialize(entry, depth + 1)).toList();
        }
        return value;
    }

    private static Object builtin(String alias, Map<String, Object> values) {
        if ("ItemStack".equals(alias)) return ItemStack.deserialize(values);
        var type = ConfigurationSerialization.getClassByAlias("ItemMeta");
        if (type == null || !type.getName().startsWith("org.bukkit.craftbukkit.")
                || !type.getName().endsWith(".SerializableMeta"))
            throw new IllegalArgumentException("unsupported built-in item meta decoder");
        return ConfigurationSerialization.deserializeObject(values, type);
    }
}
