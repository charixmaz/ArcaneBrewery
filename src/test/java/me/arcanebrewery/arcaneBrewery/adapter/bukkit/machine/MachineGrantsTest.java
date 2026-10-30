package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class MachineGrantsTest {
 @TempDir Path root;
 final UUID owner=UUID.randomUUID(),guest=UUID.randomUUID(),machine=UUID.randomUUID();
 @Test void globalGrantsPersistAndAreOwnerScoped(){var path=root.resolve("access.properties");var grants=new MachineGrants(path);grants.change(owner,"all",guest,"Guest",true);var restored=new MachineGrants(path);assertTrue(restored.permits(owner,"CASK",machine,guest));assertFalse(restored.permits(UUID.randomUUID(),"CASK",machine,guest));restored.change(owner,"all",guest,"Guest",false);assertFalse(new MachineGrants(path).permits(owner,"CASK",machine,guest));}
 @Test void categoryAndIndividualGrantsStayScoped(){var grants=new MachineGrants(null);grants.change(owner,"CULTIVATION",guest,"Guest",true);assertTrue(grants.permits(owner,"CULTIVATION",machine,guest));assertFalse(grants.permits(owner,"CASK",machine,guest));grants.change(owner,"machine-"+machine,guest,"Guest",true);assertTrue(grants.permits(owner,"CASK",machine,guest));assertFalse(grants.permits(owner,"CASK",UUID.randomUUID(),guest));}
 @Test void selfAndInvalidScopesCannotChangeAccess(){var grants=new MachineGrants(null);assertThrows(IllegalArgumentException.class,()->grants.change(owner,"all",owner,"Owner",true));assertThrows(IllegalArgumentException.class,()->grants.change(owner,"bad",guest,"Guest",true));assertTrue(grants.list(owner).isEmpty());}
 @Test void failedSaveDoesNotActivateGrant()throws Exception{var parent=root.resolve("file");Files.writeString(parent,"block");var grants=new MachineGrants(parent.resolve("access"));assertThrows(IllegalStateException.class,()->grants.change(owner,"all",guest,"Guest",true));assertFalse(grants.permits(owner,"CASK",machine,guest));}
}
