package me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.*;
/** Owners grant operation access; grants never transfer ownership or allow structure destruction. */
public final class MachineAccessCommand {
 private MachineAccessCommand() { }
 public static void run(Player owner,String[] args,BukkitMachineAccess access){try{
  if(args.length<2)throw new IllegalArgumentException("/al access grant|revoke <all|cultivation|extractor|infuser|cask|specialist|machine> <online-player> | list");
  if(args[1].equalsIgnoreCase("list")){owner.sendMessage("§bOwner: "+owner.getName());access.grants().list(owner.getUniqueId()).forEach((scope,name)->owner.sendMessage("§7"+scope.split("/")[0]+" §f→ "+name));return;}
  if(args.length!=4||!Set.of("grant","revoke").contains(args[1].toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("/al access grant|revoke <scope> <online-player>");
  boolean grant=args[1].equalsIgnoreCase("grant");
  var guest=Bukkit.getPlayerExact(args[3]);UUID guestId;String guestName;
  if(guest!=null){guestId=guest.getUniqueId();guestName=guest.getName();}
  else {if(grant)throw new IllegalArgumentException("That player must be online to verify their identity");var matches=access.grants().list(owner.getUniqueId()).entrySet().stream().filter(e->e.getValue().equalsIgnoreCase(args[3])).map(e->UUID.fromString(e.getKey().substring(e.getKey().lastIndexOf('/')+1))).distinct().toList();if(matches.size()!=1)throw new IllegalArgumentException("No unique saved grant for that name; use the access list");guestId=matches.getFirst();guestName=args[3];}

  String scope=args[2].equalsIgnoreCase("all")?"all":args[2].toUpperCase(Locale.ROOT);
  if(scope.equals("MACHINE")){var core=owner.getTargetBlockExact(6);if(core==null)throw new IllegalArgumentException("Look directly at the machine core within six blocks");var id=access.identity(core).orElseThrow(()->new IllegalArgumentException("Claim that machine first"));if(!id.ownerId().equals(owner.getUniqueId()))throw new IllegalArgumentException("Only the owner can manage access");scope="machine-"+id.machineId();}
  access.grants().change(owner.getUniqueId(),scope,guestId,guestName,grant);access.refreshAccess(owner.getUniqueId());owner.sendMessage("§aOwner: "+owner.getName()+" · "+(grant?"Granted":"Revoked")+" "+scope+" access for "+guestName);
 }catch(RuntimeException failure){owner.sendMessage("§e"+failure.getMessage());}}
}
