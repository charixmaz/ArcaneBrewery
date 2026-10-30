package me.arcanebrewery.arcaneBrewery;
import java.nio.file.*;
import java.io.IOException;
/** Copy-once upgrade: old data remains available as a recovery copy. */
public final class LegacyDataUpgrade {
 private LegacyDataUpgrade() { }
 public static void prepare(Path target)throws IOException{
  Path old=target.resolveSibling("ArcaneBrewery"),marker=target.resolve(".legacy-import-complete");
  if(!Files.isDirectory(old)||Files.exists(marker))return;
  if(Files.exists(target)){try(var files=Files.list(target)){if(files.findAny().isPresent())throw new IOException("Both ArcaneBrewery and ArcaneAlchemy data folders exist; reconcile backups before startup");}}
  Files.createDirectories(target);
  try(var paths=Files.walk(old)){for(var p:paths.toList()){var out=target.resolve(old.relativize(p));if(Files.isDirectory(p))Files.createDirectories(out);else Files.copy(p,out);}}
  Files.writeString(marker,"Copied from ArcaneBrewery; original retained\n",StandardOpenOption.CREATE_NEW);
 }
}
