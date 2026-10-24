package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MachineInventoryStorageTest {
    private MachineInventoryStorage storage() {
        return new MachineInventoryStorage("arcanebrewery","legacy_inventory","legacy_process",
                item -> new byte[]{(byte)item.getAmount()},raw -> TestItems.stack(Material.WHEAT,raw[0]));
    }
    @Test void contentsAndCapturedProcessPersistTogetherAcrossReopen() {
        var world=new MachineFixture(); var core=world.put(-17,64,33,Material.BREWING_STAND); var storage=storage();
        var items=new ItemStack[9]; items[0]=TestItems.stack(Material.WHEAT,3); items[4]=TestItems.stack(Material.WHEAT,20);
        byte[] raw=storage.save(core,null,items,"captured-duration-and-process");
        var reopened=storage().load(core);
        assertEquals("captured-duration-and-process",reopened.process()); assertEquals(20,reopened.items()[4].getAmount());
        assertArrayEquals(raw,reopened.raw());
        raw[0]^=1; reopened.items()[4].setAmount(1); assertEquals(20,storage.load(core).items()[4].getAmount());
    }
    @Test void staleSessionCannotOverwriteAndFailedEncodingDoesNotConsumeItems() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); var storage=storage();
        var items=new ItemStack[9]; items[0]=TestItems.stack(Material.WHEAT,5);
        byte[] first=storage.save(core,null,items,""); items[0].setAmount(3);
        byte[] next=storage.save(core,first,items,"processing");
        assertThrows(IllegalStateException.class,() -> storage.save(core,first,items,"stale"));
        var failing=new MachineInventoryStorage("arcanebrewery","legacy_inventory",null,item -> { throw new IllegalStateException("native encoder failed"); },raw -> null);
        assertThrows(IllegalStateException.class,() -> failing.save(core,next,items,"completed"));
        assertArrayEquals(next,storage.load(core).raw()); assertEquals("processing",storage.load(core).process());
    }
    @Test void corruptOrWrongTypePayloadIsRetainedAndCannotBeResetByEmptySave() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); var storage=storage();
        var data=core.getChunk().getPersistentDataContainer();
        data.set(storage.key(core),PersistentDataType.STRING,"bad");
        assertThrows(IllegalArgumentException.class,() -> storage.load(core));
        assertThrows(IllegalStateException.class,() -> storage.save(core,null,new ItemStack[9],""));
        assertEquals("bad",data.get(storage.key(core),PersistentDataType.STRING));
    }
    @Test void unloadedChunkNeverGetsForcedForInventoryAccess() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); world.loaded=false;
        assertThrows(IllegalStateException.class,() -> storage().load(core));
    }
    @Test void ownershipCodecIsStrictAndUuidFallbackDoesNotRequireRemoteLookup() {
        var storage=new MachineIdentityStorage("arcanebrewery"); var data=MachineFixture.data(); var key=new NamespacedKey("arcanebrewery","test_owner");
        var identity=new MachineIdentity(UUID.randomUUID(),MachineIdentity.Kind.CASK,UUID.randomUUID(),"");
        storage.write(data,key,identity); assertEquals(identity,storage.read(data,key).orElseThrow()); assertEquals(identity.ownerId().toString(),identity.displayOwner());
        assertThrows(IllegalArgumentException.class,() -> storage.write(data,key,new MachineIdentity(UUID.randomUUID(),MachineIdentity.Kind.CASK,UUID.randomUUID(),"bad|name")));
        assertEquals(identity,storage.read(data,key).orElseThrow());
    }
    @Test void missingCommittedSnapshotNeverFallsBackToLegacyBackup() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); var storage=storage();
        storage.save(core,null,new ItemStack[9],"");
        core.getChunk().getPersistentDataContainer().remove(storage.key(core));
        assertThrows(IllegalArgumentException.class,() -> storage.load(core));
        assertThrows(IllegalStateException.class,() -> storage.save(core,null,new ItemStack[9],""));
    }
}
