package me.arcanebrewery.arcaneBrewery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class LegacyDataUpgradeTest {
 @TempDir Path root;
 @Test void copiesDatabaseConfigAndKeepsRecoveryOriginal()throws Exception{var old=root.resolve("ArcaneBrewery");Files.createDirectories(old);Files.writeString(old.resolve("arcanebrewery.db"),"existing");Files.writeString(old.resolve("config.yml"),"private settings");var target=root.resolve("ArcaneAlchemy");LegacyDataUpgrade.prepare(target);assertEquals("existing",Files.readString(target.resolve("arcanebrewery.db")));assertTrue(Files.exists(old.resolve("arcanebrewery.db")));Files.writeString(target.resolve("arcanebrewery.db"),"new progress");LegacyDataUpgrade.prepare(target);assertEquals("new progress",Files.readString(target.resolve("arcanebrewery.db")));}
 @Test void conflictsDoNotOverwriteEitherCopy()throws Exception{Files.createDirectories(root.resolve("ArcaneBrewery"));Files.createDirectories(root.resolve("ArcaneAlchemy"));Files.writeString(root.resolve("ArcaneAlchemy/config.yml"),"new");assertThrows(java.io.IOException.class,()->LegacyDataUpgrade.prepare(root.resolve("ArcaneAlchemy")));assertEquals("new",Files.readString(root.resolve("ArcaneAlchemy/config.yml")));}
 @Test void newInstallDoesNotInventLegacyData()throws Exception{LegacyDataUpgrade.prepare(root.resolve("ArcaneAlchemy"));assertFalse(Files.exists(root.resolve("ArcaneBrewery")));}
}
