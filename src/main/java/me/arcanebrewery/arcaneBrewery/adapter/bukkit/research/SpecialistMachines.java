package me.arcanebrewery.arcaneBrewery.adapter.bukkit.research;
import me.arcanebrewery.arcaneBrewery.ArcaneBrewery;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.machine.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.EffectProfileMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.machine.MachineIdentity;
import me.arcanebrewery.arcaneBrewery.application.research.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
/** Four real processes share a bounded transactional inventory and the existing owner/session boundary. */
public final class SpecialistMachines implements Listener,AutoCloseable {
 private static final NamespacedKey HISTORY=new NamespacedKey("arcanebrewery","specialist_history_v1");
 private static final int[] SLOTS={0,1,2,3,4,5,6,7,8},EDIT={0,1,2};
 private static final List<ProfileEffect> REMOVABLE=List.of(ProfileEffect.HUNGER,ProfileEffect.SLOWNESS,ProfileEffect.WEAKNESS,ProfileEffect.WITHER,ProfileEffect.CURSE,ProfileEffect.HALLUCINATION,ProfileEffect.LEVITATION);
 private static final class Holder implements MachineGuiHolder {Inventory inventory;public Inventory getInventory(){return inventory;}}
 private static final class Session {final Block core;final Inventory gui;byte[] raw;SpecialistState state;Session(Block b,Inventory g,byte[] raw,SpecialistState s){core=b;gui=g;this.raw=raw;state=s;}}
 private final ArcaneBrewery plugin;private final BukkitMachineAccess access;private final MachineInventoryStorage storage;private final Map<Inventory,Session> sessions=new IdentityHashMap<>();private final BukkitTask timer;
 public SpecialistMachines(ArcaneBrewery plugin){this.plugin=plugin;access=plugin.machineAccess();storage=new MachineInventoryStorage("arcanebrewery","specialist_legacy",null);
  access.occupancy(MachineIdentity.Kind.SPECIALIST,b->{var saved=storage.load(b);return Arrays.stream(saved.items()).anyMatch(Objects::nonNull)||!saved.process().isEmpty()&&SpecialistState.decode(saved.process()).readyAt()>0;});
  timer=Bukkit.getScheduler().runTaskTimer(plugin,()->{for(var s:List.copyOf(sessions.values()))try{if(s.gui.getViewers().isEmpty()){sessions.remove(s.gui);continue;}recover(s);}catch(RuntimeException bad){access.closeCore(s.core);sessions.remove(s.gui);}},20,20);
 }
 static Block coreFor(Block clicked) {
  for(var type:SpecialistType.values()) if(type.acceptsFoundation(clicked.getType().name())) {
   var above=clicked.getRelative(0,1,0);
   if(structure(above)==type)return above;
  }
  return clicked;
 }
 static SpecialistType structure(Block core){for(var t:SpecialistType.values())if(core.getType().name().equals(t.core)&&t.acceptsFoundation(core.getRelative(0,-1,0).getType().name()))return t;return null;}
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void open(PlayerInteractEvent event){
  if(event.getAction()!=Action.RIGHT_CLICK_BLOCK||event.getHand()!=EquipmentSlot.HAND||event.getClickedBlock()==null)return;
  final Block core=coreFor(event.getClickedBlock());var type=structure(core);if(type==null)return;
  event.setCancelled(true);var player=event.getPlayer();
  try{
   if(!PlayerResearch.read(player).earned().contains(type.name())){player.sendMessage("§eUnlock "+type.label+" for "+type.levels+" XP levels: /brew guide → Machines & Controls.");return;}
   if(!player.isSneaking()&&access.identity(core).isEmpty()){player.sendMessage("§eStructure correct. Sneak-right-click the core to claim and construct "+type.label+".");return;}
   var saved=storage.load(core);var state=saved.process().isBlank()?new SpecialistState(type,false,0,0):SpecialistState.decode(saved.process());if(state.type()!=type)throw new IllegalArgumentException("Stored machine type mismatch");
   access.open(player,core,MachineIdentity.Kind.SPECIALIST,()->{
    var holder=new Holder();holder.inventory=Bukkit.createInventory(holder,9,type.label);var s=new Session(core,holder.inventory,saved.raw(),state);
    var data=saved.items();for(int i=3;i<9;i++)if(data[i]!=null)throw new IllegalArgumentException("Protected slot contains data");
    for(int i=0;i<3;i++)s.gui.setItem(i,data[i]);sessions.put(s.gui,s);if(s.raw==null)commit(s,data,state);recover(s);paint(s);return s.gui;
   });
  }catch(RuntimeException bad){plugin.getLogger().warning("Specialist open failed ("+type.name()+"): "+bad);player.sendMessage("§cSpecialist data unavailable. Stored items retained; check server log.");}
 }
 private ItemStack[] contents(Session s){var items=new ItemStack[9];for(int i=0;i<3;i++){var item=s.gui.getItem(i);items[i]=item==null?null:item.clone();}return items;}
 private void commit(Session s,ItemStack[] items,SpecialistState state){var raw=storage.save(s.core,s.raw,items,state.encode());s.raw=raw;s.state=state;for(int i=0;i<3;i++)s.gui.setItem(i,items[i]);paint(s);}
 private void recover(Session s){if(structure(s.core)!=s.state.type())return;var next=s.state.complete(System.currentTimeMillis());if(!next.equals(s.state))commit(s,contents(s),next);}
 private boolean accepts(Session s,int slot,ItemStack item){
  if(slot==2||DisplayOnlyItems.test(item))return false;
  if(!s.state.built())return item.getType().name().equals(slot==0?s.state.type().buildA:s.state.type().buildB)&&!item.hasItemMeta();
  if(s.state.type()==SpecialistType.REAGENT_PROCESSOR)return slot==0?Set.of(Material.REDSTONE,Material.GLOWSTONE_DUST,Material.QUARTZ).contains(item.getType()):Set.of(Material.AMETHYST_SHARD,Material.BLAZE_ROD,Material.HONEYCOMB).contains(item.getType());
  if(slot==0)return item.getType()==Material.POTION&&item.getAmount()==1&&new EffectProfileMetadata("arcanebrewery").read(item.getItemMeta()).isPresent();
  return PreparedReagents.read(item).map(k->k==required(s.state.type())).orElse(false);
 }
 private PreparedReagents.Kind required(SpecialistType type){return switch(type){case DURATION_INFUSER->PreparedReagents.Kind.DURATION;case ESSENCE_CONCENTRATOR->PreparedReagents.Kind.POTENCY;case PURIFICATION_CHAMBER->PreparedReagents.Kind.PURIFICATION;default->throw new IllegalArgumentException();};}
 @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void click(InventoryClickEvent event){
  var s=sessions.get(event.getView().getTopInventory());if(s==null)return;event.setCancelled(true);
  if(!(event.getWhoClicked() instanceof Player player)||!access.active(s.gui,player.getUniqueId())||structure(s.core)!=s.state.type()||event instanceof InventoryCreativeEvent)return;
  try{recover(s);if(s.state.readyAt()>0)return;
   if(event.isShiftClick()){ShiftTransfers.move(event,contents(s),SLOTS,EDIT,i->i==0&&s.state.type()!=SpecialistType.REAGENT_PROCESSOR&&s.state.built()?1:64,(slot,item)->accepts(s,slot,item),next->commit(s,next,s.state));return;}
   if(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT)return;if(event.getClickedInventory()!=s.gui){event.setCancelled(false);return;}
   int slot=event.getSlot();if(slot==6){commit(s,contents(s),new SpecialistState(s.state.type(),s.state.built(),0,(s.state.selection()+1)%REMOVABLE.size()));return;}
   if(slot==8){start(s);return;}if(slot>2)return;
   var cursor=player.getItemOnCursor();if(cursor!=null&&!cursor.getType().isAir()&&!accepts(s,slot,cursor))return;
   var move=me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemySlotTransfer.move(s.gui.getItem(slot),cursor,event.getClick()==ClickType.RIGHT,slot==0&&s.state.built()&&s.state.type()!=SpecialistType.REAGENT_PROCESSOR?1:64);
   var next=contents(s);next[slot]=move.slot();commit(s,next,s.state);player.setItemOnCursor(move.cursor());
  }catch(RuntimeException rejected){player.sendMessage("§e"+rejected.getMessage());}
 }
 private void start(Session s){var captured=contents(s);var work=SpecialistWork.start(captured,s.state,System.currentTimeMillis());commit(s,work.items(),work.state());for(var viewer:s.gui.getViewers())if(viewer instanceof Player player)plugin.reconcileMaterialInputs(player,new ItemStack[]{captured[0],captured[1]});}
 private void control(Session s,int slot,Material icon,String title,List<String> lore){var item=new ItemStack(icon);var meta=item.getItemMeta();DisplayOnlyItems.mark(meta);meta.setDisplayName("§d"+title);meta.setLore(lore);item.setItemMeta(meta);s.gui.setItem(slot,item);}
 private void paint(Session s){try{render(s);}catch(RuntimeException failure){plugin.getLogger().warning("Specialist display unavailable; saved state retained.");}}
 private void render(Session s){
  for(int i=3;i<9;i++)control(s,i,Material.GRAY_STAINED_GLASS_PANE,"",List.of());var t=s.state.type();
  control(s,4,Material.BOOK,t.label,List.of("Owner: "+access.ownerLabel(s.core),"Inputs: first two slots; output: third slot.",s.state.built()?"Built permanently on this core":"Build: "+t.amountA+" "+t.buildA+" + "+t.amountB+" "+t.buildB,"Shift-click supported; outputs stay locked during work."));
  if(s.state.built())control(s,5,Material.PAPER,"Process Inputs",t==SpecialistType.REAGENT_PROCESSOR?List.of("§eSlot 1: 4 raw items · Slot 2: 1 binder", "§bRedstone + Amethyst Shard → Duration", "§bGlowstone Dust + Blaze Rod → Potency", "§bNether Quartz + Honeycomb → Purification", "§7Exact recipe icons: /brew guide → Machines"):List.of("First: one versioned finished elixir.","Second: one prepared "+required(t).name().toLowerCase(Locale.ROOT)+" reagent.","Old single-effect and unfinished bottles stay unchanged.","No repeat pass of this type on the same bottle."));
  if(t==SpecialistType.PURIFICATION_CHAMBER)control(s,6,Material.MILK_BUCKET,"Remove: "+REMOVABLE.get(s.state.selection()),List.of("Click to select one unwanted effect."));
  control(s,8,Material.ANVIL,s.state.readyAt()>0?"Processing":s.state.built()?"Start (10 seconds)":"Construct",List.of("Consumes the displayed inputs once.","Collect output before the next operation."));
 }
 @EventHandler public void close(InventoryCloseEvent event){sessions.remove(event.getInventory());}
 @EventHandler public void unload(ChunkUnloadEvent event){for(var s:List.copyOf(sessions.values()))if(s.core.getChunk().equals(event.getChunk())){access.closeCore(s.core);sessions.remove(s.gui);}}
 public void close(){timer.cancel();for(var s:List.copyOf(sessions.values()))access.closeCore(s.core);sessions.clear();}
}
