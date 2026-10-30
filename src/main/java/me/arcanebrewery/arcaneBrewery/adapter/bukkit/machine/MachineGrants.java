package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** Owner-scoped grants. Saving replaces one file; a failed write does not change active permissions. */
public final class MachineGrants {
 private final Path path;private Properties entries=new Properties();
 public MachineGrants(Path path){this.path=path;if(path!=null&&Files.exists(path))try(var in=Files.newInputStream(path)){entries.load(in);}catch(IOException e){throw new IllegalStateException("Cannot read machine access grants",e);}}
 public boolean permits(UUID owner,String kind,UUID machine,UUID guest){return entries.containsKey(key(owner,"all",guest))||entries.containsKey(key(owner,kind,guest))||entries.containsKey(key(owner,"machine-"+machine,guest));}
 private static String key(UUID owner,String scope,UUID guest){return owner+"/"+scope+"/"+guest;}
 public Map<String,String> list(UUID owner){var out=new TreeMap<String,String>();entries.forEach((k,v)->{if(k.toString().startsWith(owner+"/"))out.put(k.toString().substring(37),v.toString());});return Map.copyOf(out);}
 public void change(UUID owner,String scope,UUID guest,String name,boolean grant){
  if(owner.equals(guest))throw new IllegalArgumentException("The owner already has access");
  if(!scope.matches("all|EXTRACTOR|CULTIVATION|INFUSER|CASK|SPECIALIST|machine-[0-9a-f-]{36}"))throw new IllegalArgumentException("Unknown machine scope");
  var next=new Properties();next.putAll(entries);if(grant)next.setProperty(key(owner,scope,guest),name);else next.remove(key(owner,scope,guest));
  if(next.size()>10000)throw new IllegalArgumentException("Machine access limit reached");
  if(path!=null)try{Files.createDirectories(path.getParent());var temp=Files.createTempFile(path.getParent(),"access-",".tmp");try{try(var out=Files.newOutputStream(temp)){next.store(out,"ArcaneAlchemy machine access; UUIDs are authoritative");}Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}catch(IOException e){throw new IllegalStateException("Access was not changed: save failed",e);}
  entries=next;
 }
}
