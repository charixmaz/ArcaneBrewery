package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;

import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity.Kind;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.TestItems;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BukkitMachineAccessTest {
    @Test void publicRenameKeepsLegacyOwnershipNamespace(){var w=new MachineFixture();var core=w.put(0,64,0,Material.BARREL);var owner=w.player(UUID.randomUUID(),false);var branded=MachineFixture.proxy(org.bukkit.plugin.Plugin.class,(name,args)->switch(name){case "getName"->"ArcaneAlchemy";case "isEnabled"->true;case "getLogger"->java.util.logging.Logger.getLogger("test");default->throw new UnsupportedOperationException(name);});var access=new BukkitMachineAccess(branded);access.authorize(owner,core,Kind.CULTIVATION,true);assertTrue(new MachineIdentityStorage("arcanebrewery").read(core).isPresent());}
    @Test void guestsOperateButCannotBreakAndRevocationClosesTheirSession(){
        var w=new MachineFixture();var core=w.put(0,64,0,Material.BARREL);var access=new BukkitMachineAccess(w.plugin);var owner=w.player(UUID.randomUUID(),false);var guest=w.player(UUID.randomUUID(),false);
        access.authorize(owner,core,Kind.CULTIVATION,true);access.grants().change(owner.getUniqueId(),"all",guest.getUniqueId(),"Guest",true);
        assertTrue(access.permitted(guest,core));assertTrue(access.open(guest,core,Kind.CULTIVATION,()->w.inventory(null)));
        var broken=new org.bukkit.event.block.BlockBreakEvent(core,guest);access.onBreak(broken);assertTrue(broken.isCancelled());
        access.grants().change(owner.getUniqueId(),"all",guest.getUniqueId(),"Guest",false);access.refreshAccess(owner.getUniqueId());assertFalse(access.permitted(guest,core));
    }
    @Test void ownershipPersistsAcrossServiceRecreationWithoutTileStateOrNameLookup() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BREWING_STAND);
        var owner=world.player(UUID.randomUUID(),false); var stranger=world.player(UUID.randomUUID(),false);
        var access=new BukkitMachineAccess(world.plugin);
        assertTrue(access.authorize(owner,core,Kind.EXTRACTOR,true));
        var original=access.identity(core).orElseThrow();
        var reopened=new BukkitMachineAccess(world.plugin);
        assertEquals(original,reopened.identity(core).orElseThrow());
        assertTrue(reopened.permitted(owner,core)); assertFalse(reopened.permitted(stranger,core));
        assertFalse(reopened.authorize(stranger,core,Kind.EXTRACTOR,true));
        assertEquals(original,reopened.identity(core).orElseThrow());
    }
    @Test void adminBypassDoesNotTransferOwnershipOrPermitConcurrentViews() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.ENCHANTING_TABLE);
        var access=new BukkitMachineAccess(world.plugin);
        var owner=world.player(UUID.randomUUID(),false); var admin=world.player(UUID.randomUUID(),true);
        assertTrue(access.open(owner,core,Kind.INFUSER,() -> world.inventory(null)));
        var identity=access.identity(core).orElseThrow();
        assertTrue(access.permitted(admin,core)); assertFalse(access.open(admin,core,Kind.INFUSER,() -> world.inventory(null)));
        access.onClose(new InventoryCloseEvent(owner.getOpenInventory()));
        assertTrue(access.open(admin,core,Kind.INFUSER,() -> world.inventory(null)));
        assertEquals(identity,access.identity(core).orElseThrow()); access.close();
    }
    @Test void staleCloseFromAnotherPlayerDoesNotReleaseOwnerSession() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); var access=new BukkitMachineAccess(world.plugin);
        var owner=world.player(UUID.randomUUID(),false); var other=world.player(UUID.randomUUID(),true); var inv=world.inventory(null);
        assertTrue(access.open(owner,core,Kind.CASK,() -> inv));
        access.onClose(new InventoryCloseEvent(world.view(inv,other)));
        assertTrue(access.active(inv,owner.getUniqueId())); assertFalse(access.open(other,core,Kind.CASK,() -> world.inventory(null)));
        access.close(); assertFalse(access.active(inv,owner.getUniqueId()));
    }
    @Test void ordinaryBlocksAreNotClaimedByRecognitionAndInvalidStructureIsNotAMachine() {
        var world=new MachineFixture(); var access=new BukkitMachineAccess(world.plugin);
        var barrel=world.put(0,64,0,Material.BARREL); var stand=world.put(4,64,0,Material.BREWING_STAND);
        assertNull(access.recognized(barrel)); assertNull(access.recognized(stand));
        assertTrue(access.identity(barrel).isEmpty()); assertTrue(access.identity(stand).isEmpty());
        world.put(4,63,0,Material.CRAFTING_TABLE);
        assertEquals(Kind.EXTRACTOR,access.recognized(stand)); assertTrue(access.identity(stand).isEmpty());
    }
    @Test void unauthorizedStructureBreakIsDeniedAndOccupiedCoreCannotBeBypassed() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BREWING_STAND); var support=world.put(0,63,0,Material.CRAFTING_TABLE);
        var owner=world.player(UUID.randomUUID(),false); var outsider=world.player(UUID.randomUUID(),false);
        var admin=world.player(UUID.randomUUID(),true); var access=new BukkitMachineAccess(world.plugin);
        access.authorize(owner,core,Kind.EXTRACTOR,true); access.occupancy(Kind.EXTRACTOR,b -> false);
        var denied=new BlockBreakEvent(support,outsider); access.onBreak(denied); assertTrue(denied.isCancelled());
        var allowed=new BlockBreakEvent(support,owner); access.onBreak(allowed); assertFalse(allowed.isCancelled());
        access.occupancy(Kind.EXTRACTOR,b -> true);
        var full=new BlockBreakEvent(core,admin); access.onBreak(full); assertTrue(full.isCancelled());
        assertTrue(access.identity(core).isPresent());
    }
    @Test void pistonMovementAndAutomationAreBlockedOnlyForRecognizedMachines() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); var ordinary=world.put(10,64,0,Material.BARREL);
        var access=new BukkitMachineAccess(world.plugin); access.authorize(world.player(UUID.randomUUID(),false),core,Kind.CULTIVATION,true);
        var item=TestItems.stack(Material.WHEAT,1);
        var protectedMove=new InventoryMoveItemEvent(world.inventory(ordinary),item,world.inventory(core),true);
        access.onMove(protectedMove); assertTrue(protectedMove.isCancelled());
        var normalMove=new InventoryMoveItemEvent(world.inventory(ordinary),item,world.inventory(ordinary),true);
        access.onMove(normalMove); assertFalse(normalMove.isCancelled());
        var piston=new BlockPistonExtendEvent(world.block(-1,64,0),List.of(core),BlockFace.EAST);
        access.onPiston(piston); assertTrue(piston.isCancelled());
    }
    @Test void malformedOwnershipFailsClosedAndRemainsStored() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); var access=new BukkitMachineAccess(world.plugin);
        var storage=new MachineIdentityStorage("arcanebrewery");
        core.getChunk().getPersistentDataContainer().set(storage.key(core),org.bukkit.persistence.PersistentDataType.STRING,"corrupt");
        var player=world.player(UUID.randomUUID(),true);
        assertFalse(access.permitted(player,core)); assertFalse(access.authorize(player,core,Kind.CASK,true));
        assertEquals("corrupt",core.getChunk().getPersistentDataContainer().get(storage.key(core),org.bukkit.persistence.PersistentDataType.STRING));
    }
    @Test void cancelledFirstOpenDoesNotLeaveAClaimOrStaleLock() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BREWING_STAND); var access=new BukkitMachineAccess(world.plugin);
        var denied=world.player(UUID.randomUUID(),false,true);
        assertFalse(access.open(denied,core,Kind.EXTRACTOR,() -> world.inventory(null)));
        assertTrue(access.identity(core).isEmpty());
        var owner=world.player(UUID.randomUUID(),false);
        assertTrue(access.open(owner,core,Kind.EXTRACTOR,() -> world.inventory(null))); access.close();
    }
    @Test void laterCancelledNativeOpenReleasesOnlyItsOwnLease() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL); var access=new BukkitMachineAccess(world.plugin);
        var owner=world.player(UUID.randomUUID(),false); var inv=world.inventory(core);
        access.authorize(owner,core,Kind.CULTIVATION,true);
        var event=new InventoryOpenEvent(world.view(inv,owner)); access.onOpen(event);
        assertTrue(access.active(inv,owner.getUniqueId())); event.setCancelled(true); access.afterOpen(event);
        assertFalse(access.active(inv,owner.getUniqueId())); assertEquals(owner.getUniqueId(),access.identity(core).orElseThrow().ownerId());
    }
    @Test void virtualControlsRejectAutomationEvenWithoutAnActiveViewer() {
        var world=new MachineFixture(); var access=new BukkitMachineAccess(world.plugin);
        var marker=new MachineAutomationHolder() { public org.bukkit.inventory.Inventory getInventory() { return null; } };
        var virtual=MachineFixture.proxy(org.bukkit.inventory.Inventory.class,(name,args) -> {
            if (name.equals("getHolder")) return marker; throw new UnsupportedOperationException(name);
        });
        var move=new InventoryMoveItemEvent(virtual,TestItems.stack(Material.WHEAT,1),world.inventory(null),true);
        access.onMove(move); assertTrue(move.isCancelled());
    }
    @Test void emptyUnownedSnapshotDoesNotClaimOrRecognizeAReplacementVanillaBlock() {
        var world=new MachineFixture(); var core=world.put(0,64,0,Material.BARREL);
        new MachineInventoryStorage("arcanebrewery","legacy",null).save(core,null,new org.bukkit.inventory.ItemStack[9],"");
        var access=new BukkitMachineAccess(world.plugin);
        assertNull(access.recognized(core)); assertTrue(access.identity(core).isEmpty());
    }
}
