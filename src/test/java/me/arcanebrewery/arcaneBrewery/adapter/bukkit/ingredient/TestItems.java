package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.*;
import org.bukkit.potion.PotionType;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Small API-boundary doubles, not a server emulator. Metadata copies match ItemStack's contract. */
public final class TestItems {
    private TestItems() {}
    public static Stack stack(Material material, int amount) { return new Stack(material, amount, new Meta(false)); }
    public static Stack water() {
        var item = new Stack(Material.POTION, 1, new Meta(true));
        item.edit(meta -> ((PotionMeta) meta).setBasePotionType(PotionType.WATER));
        return item;
    }
    static Inventory inventory(ItemStack... contents) {
        return (Inventory) Proxy.newProxyInstance(Inventory.class.getClassLoader(), new Class<?>[]{Inventory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSize" -> contents.length;
                    case "getItem" -> contents[(int) args[0]];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    public static final class Stack extends ItemStack {
        private final Material material;
        private int amount;
        private Meta meta;
        Stack(Material material, int amount, Meta meta) { super(); this.material = material; this.amount = amount; this.meta = meta; }
        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public int getMaxStackSize() { return material == Material.POTION ? 1 : 64; }
        @Override public boolean isSimilar(ItemStack other) {
            return other instanceof Stack stack && material == stack.material && meta.values.equals(stack.meta.values)
                    && meta.data.entries.equals(stack.meta.data.entries);
        }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public boolean hasItemMeta() { return !meta.values.isEmpty() || !meta.data.entries.isEmpty(); }
        @Override public ItemMeta getItemMeta() { return meta.copy().proxy(); }
        @Override public boolean setItemMeta(ItemMeta value) {
            meta = ((Meta) Proxy.getInvocationHandler(value)).copy();
            return true;
        }
        @Override public Stack clone() { return new Stack(material, amount, meta.copy()); }
        public void edit(Consumer<ItemMeta> editor) {
            var value = getItemMeta(); editor.accept(value); setItemMeta(value);
        }
    }

    private static final class Meta implements InvocationHandler {
        final boolean potion;
        final Map<String, Object> values = new HashMap<>();
        final Data data = new Data();
        Meta(boolean potion) { this.potion = potion; }
        Meta copy() {
            var copy = new Meta(potion);
            values.forEach((key, value) -> copy.values.put(key, value instanceof List<?> list ? new ArrayList<>(list) : value instanceof Set<?> set ? new HashSet<>(set) : value));
            copy.data.entries.putAll(data.entries);
            return copy;
        }
        ItemMeta proxy() {
            return (ItemMeta) Proxy.newProxyInstance(ItemMeta.class.getClassLoader(),
                    new Class<?>[]{potion ? PotionMeta.class : ItemMeta.class}, this);
        }
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "clone" -> copy().proxy();
                case "getPersistentDataContainer" -> data.proxy();
                case "hasLore" -> values.containsKey("Lore");
                case "getLore" -> values.containsKey("Lore") ? new ArrayList<>((List<?>) values.get("Lore")) : null;
                case "hasDisplayName" -> values.containsKey("DisplayName");
                case "getDisplayName" -> values.getOrDefault("DisplayName", "");
                case "displayName" -> {
                    if (args==null || args.length==0) yield values.get("DisplayComponent");
                    values.put("DisplayComponent",args[0]);
                    values.put("DisplayName",net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize((net.kyori.adventure.text.Component)args[0]));
                    yield null;
                }
                case "lore" -> {
                    if (args==null || args.length==0) yield values.get("LoreComponents");
                    @SuppressWarnings("unchecked") var components=(List<net.kyori.adventure.text.Component>)args[0];
                    values.put("LoreComponents",List.copyOf(components));
                    values.put("Lore",components.stream().map(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()::serialize).toList());
                    yield null;
                }
                case "hasCustomModelData" -> values.containsKey("CustomModelData");
                case "getCustomModelData" -> values.get("CustomModelData");
                case "isUnbreakable" -> values.getOrDefault("Unbreakable", false);
                case "getBasePotionType" -> values.get("BasePotionType");
                case "hasCustomEffects" -> values.getOrDefault("CustomEffects", false);
                case "getCustomEffects" -> List.of();
                case "getItemFlags" -> Set.copyOf((Set<?>)values.getOrDefault("ItemFlags",Set.of()));
                case "hasItemFlag" -> ((Set<?>)values.getOrDefault("ItemFlags",Set.of())).contains(args[0]);
                case "addItemFlags" -> {
                    @SuppressWarnings("unchecked") var flags=(Set<ItemFlag>)values.computeIfAbsent("ItemFlags",ignored -> new HashSet<ItemFlag>());
                    flags.addAll(Arrays.asList((ItemFlag[])args[0])); yield null;
                }
                case "setDisplayName", "setLore", "setCustomModelData", "setUnbreakable", "setBasePotionType" -> {
                    values.put(method.getName().substring(3), args[0] instanceof List<?> list ? new ArrayList<>(list) : args[0]);
                    yield null;
                }
                case "toString" -> "TestItemMeta";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }

    private static final class Data implements InvocationHandler {
        record Entry(PersistentDataType<?, ?> type, Object value) {}
        final Map<NamespacedKey, Entry> entries = new HashMap<>();
        PersistentDataContainer proxy() {
            return (PersistentDataContainer) Proxy.newProxyInstance(PersistentDataContainer.class.getClassLoader(),
                    new Class<?>[]{PersistentDataContainer.class}, this);
        }
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "set" -> { entries.put((NamespacedKey) args[0], new Entry((PersistentDataType<?, ?>) args[1], args[2])); yield null; }
                case "remove" -> { entries.remove(args[0]); yield null; }
                case "has" -> entries.containsKey(args[0]) && (args.length == 1 || matches(entries.get(args[0]), args[1]));
                case "get", "getOrDefault" -> {
                    var entry = entries.get(args[0]);
                    yield entry != null && matches(entry, args[1]) ? entry.value()
                            : method.getName().equals("getOrDefault") ? args[2] : null;
                }
                case "getKeys" -> Set.copyOf(entries.keySet());
                case "isEmpty" -> entries.isEmpty();
                case "toString" -> "TestPersistentDataContainer";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
        boolean matches(Entry entry, Object type) {
            return entry.type().getPrimitiveType().equals(((PersistentDataType<?, ?>) type).getPrimitiveType());
        }
    }
}
