package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAlchemyInventoryReaderTest {
    private static final AtomicBoolean EXECUTED = new AtomicBoolean();
    private static final class Gadget implements Serializable {
        private void readObject(ObjectInputStream in) throws IOException,ClassNotFoundException {
            EXECUTED.set(true); in.defaultReadObject();
        }
    }
    private String stream(Object first, boolean trailing) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try(var out = new ObjectOutputStream(bytes)) {
            out.writeInt(3); out.writeObject(first); out.writeObject(null); out.writeObject(null);
            if(trailing) out.writeObject("extra");
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    private Object wrapper(Map<String,Object> values) throws Exception {
        var constructor = Class.forName("org.bukkit.util.io.Wrapper").getDeclaredConstructor(Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }
    @Test void unknownJavaClassesAreRejectedBeforeTheirReadObjectExecutes() throws Exception {
        EXECUTED.set(false);
        String payload = stream(new Gadget(),false);
        assertThrows(IllegalArgumentException.class, () -> new LegacyAlchemyInventoryReader().read(payload));
        assertFalse(EXECUTED.get());
    }
    @Test void onlyBuiltInItemAliasesMayReachConfigurationDecoder() throws Exception {
        var called = new AtomicBoolean();
        var reader = new LegacyAlchemyInventoryReader((alias,map) -> {
            called.set(true); return TestItems.stack(Material.WHEAT,3);
        });
        String malicious = stream(wrapper(Map.of("==","ExternalPluginGadget","value",1)),false);
        assertThrows(IllegalArgumentException.class, () -> reader.read(malicious));
        assertFalse(called.get());
        String valid = stream(wrapper(com.google.common.collect.ImmutableMap.of("==","ItemStack","type","WHEAT","amount",3)),false);
        var items = reader.read(valid);
        assertTrue(called.get()); assertEquals(3,items[0].getAmount()); assertNull(items[1]); assertNull(items[2]);
    }
    @Test void badEncodingOversizedPayloadsAndTrailingObjectsFailClosed() throws Exception {
        var reader = new LegacyAlchemyInventoryReader();
        assertThrows(IllegalArgumentException.class, () -> reader.read("%%%"));
        assertThrows(IllegalArgumentException.class, () -> reader.read("A".repeat(1_398_105)));
        String trailing = stream(null,true);
        assertThrows(IllegalArgumentException.class, () -> reader.read(trailing));
        assertEquals(3,reader.read(stream(null,false)).length);
    }
}
