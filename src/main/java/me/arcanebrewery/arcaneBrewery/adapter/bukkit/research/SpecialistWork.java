package me.arcanebrewery.arcaneBrewery.adapter.bukkit.research;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.EffectProfileMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.discovery.DisplayOnlyItems;
import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.application.research.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
/** Side-effect-free inventory transaction planner shared by the live GUI and recovery tests. */
public final class SpecialistWork {
 private SpecialistWork() { }
 private static final NamespacedKey HISTORY=new NamespacedKey("arcanebrewery","specialist_history_v1");
 public static final List<ProfileEffect> REMOVABLE=List.of(ProfileEffect.HUNGER,ProfileEffect.SLOWNESS,ProfileEffect.WEAKNESS,ProfileEffect.WITHER,ProfileEffect.CURSE,ProfileEffect.HALLUCINATION,ProfileEffect.LEVITATION);
 public record Work(ItemStack[] items,SpecialistState state) {
  public Work {items=copy(items);Objects.requireNonNull(state);}
  @Override public ItemStack[] items(){return copy(items);}
  private static ItemStack[] copy(ItemStack[] values){return Arrays.stream(values).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
 }
 private static PreparedReagents.Kind required(SpecialistType t){return switch(t){case DURATION_INFUSER->PreparedReagents.Kind.DURATION;case ESSENCE_CONCENTRATOR->PreparedReagents.Kind.POTENCY;case PURIFICATION_CHAMBER->PreparedReagents.Kind.PURIFICATION;default->throw new IllegalArgumentException();};}
 private static void require(ItemStack item,String material,int amount){if(item==null||!item.getType().name().equals(material)||item.getAmount()<amount||DisplayOnlyItems.test(item))throw new IllegalArgumentException("Need "+amount+" "+material);}
 private static void use(ItemStack[] items,int slot,int count){if(items[slot].getAmount()==count)items[slot]=null;else items[slot].setAmount(items[slot].getAmount()-count);}
 public static Work start(ItemStack[] input,SpecialistState state,long now){return start(input,state,now,ItemStack::new);}
 public static Work start(ItemStack[] input,SpecialistState state,long now,java.util.function.Function<Material,ItemStack> factory){
  if(input.length!=9||now<0)throw new IllegalArgumentException("Invalid specialist input snapshot");
  for(int i=3;i<9;i++)if(input[i]!=null)throw new IllegalArgumentException("Protected slot contains an item");
  if(state.readyAt()>0)throw new IllegalStateException("Processing inputs are locked");
  var items=Arrays.stream(input).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);var t=state.type();if(items[2]!=null)throw new IllegalArgumentException("Collect output first");
  if(!state.built()){
   require(items[0],t.buildA,t.amountA);require(items[1],t.buildB,t.amountB);use(items,0,t.amountA);use(items,1,t.amountB);return new Work(items,new SpecialistState(t,true,0,0));
  }
  if(items[0]==null||items[1]==null)throw new IllegalArgumentException("Supply both inputs");
  if(t==SpecialistType.REAGENT_PROCESSOR){
   PreparedReagents.Kind kind;String second;
   switch(items[0].getType()){case REDSTONE-> {kind=PreparedReagents.Kind.DURATION;second="AMETHYST_SHARD";}case GLOWSTONE_DUST->{kind=PreparedReagents.Kind.POTENCY;second="BLAZE_ROD";}case QUARTZ->{kind=PreparedReagents.Kind.PURIFICATION;second="HONEYCOMB";}default->throw new IllegalArgumentException("Use a documented raw reagent pair");}
   require(items[0],items[0].getType().name(),4);require(items[1],second,1);items[2]=PreparedReagents.create(kind,factory);use(items,0,4);use(items,1,1);
  }else{
   if(items[0].getType()!=Material.POTION||items[0].getAmount()!=1||DisplayOnlyItems.test(items[0])||(t!=SpecialistType.PURIFICATION_CHAMBER&&PreparedReagents.read(items[1]).orElseThrow()!=required(t)))throw new IllegalArgumentException("One versioned finished elixir and matching prepared reagent required");
   var meta=items[0].getItemMeta();var finalData=new FinalDrinkMetadata("arcanebrewery");if(finalData.unfinished(meta)||finalData.prematurePoison(meta)||finalData.readConsumption(meta).isEmpty())throw new IllegalArgumentException("A finished usable elixir is required");
   var metadata=new EffectProfileMetadata("arcanebrewery");var profile=metadata.read(meta).orElseThrow(()->new IllegalArgumentException("Historical single-effect bottles cannot be specialist-processed"));
   if(meta.getPersistentDataContainer().has(HISTORY)&&!meta.getPersistentDataContainer().has(HISTORY,PersistentDataType.INTEGER))throw new IllegalArgumentException("Invalid specialist history type");
   int history=meta.getPersistentDataContainer().getOrDefault(HISTORY,PersistentDataType.INTEGER,0);
   int consumed=1;SpecialistProcess.Result result;
   if(t==SpecialistType.PURIFICATION_CHAMBER){
    if((history&4)!=0)throw new IllegalArgumentException("This historical bottle was already purified");
    boolean prepared=PreparedReagents.read(items[1]).filter(k->k==PreparedReagents.Kind.PURIFICATION).isPresent();
    if(DisplayOnlyItems.test(items[1])||items[1].hasItemMeta()&&!prepared)throw new IllegalArgumentException("Use ordinary reduction materials");
    int seconds,cap;switch(items[1].getType()){case SUGAR->{seconds=1;cap=25;}case HONEYCOMB->{seconds=3;cap=35;}case AMETHYST_SHARD->{seconds=5;cap=45;}default->{if(!prepared)throw new IllegalArgumentException("Use Sugar, Honeycomb or Amethyst Shard");seconds=5;cap=45;}}
    var reductionKey=new NamespacedKey("arcanebrewery","drawback_reduction_v1");
    if(meta.getPersistentDataContainer().has(reductionKey)&&!meta.getPersistentDataContainer().has(reductionKey,PersistentDataType.INTEGER))throw new IllegalArgumentException("Invalid reduction history");
    int already=meta.getPersistentDataContainer().getOrDefault(reductionKey,PersistentDataType.INTEGER,0);
    var reduced=DrawbackReduction.apply(profile,REMOVABLE.get(state.selection()),seconds,cap,items[1].getAmount(),already);
    consumed=reduced.consumed();meta.getPersistentDataContainer().set(reductionKey,PersistentDataType.INTEGER,reduced.totalSeconds());result=new SpecialistProcess.Result(reduced.profile(),history);
   }else result=SpecialistProcess.apply(t,profile,history,REMOVABLE.get(state.selection()));
   var logKey=new NamespacedKey("arcanebrewery","specialist_log_v1");
   String previous=meta.getPersistentDataContainer().getOrDefault(logKey,PersistentDataType.STRING,"1");
   if(previous.length()>8192)throw new IllegalArgumentException("Processing history too large");
   var codec=new EffectProfileCodec();String record=t.name()+"@"+now+":"+java.util.Base64.getEncoder().encodeToString(codec.encode(profile))+":"+java.util.Base64.getEncoder().encodeToString(codec.encode(result.profile()));
   meta.getPersistentDataContainer().set(logKey,PersistentDataType.STRING,previous+"|"+record);
   metadata.write(meta,result.profile());meta.getPersistentDataContainer().set(HISTORY,PersistentDataType.INTEGER,result.history());
   me.arcanebrewery.arcaneBrewery.adapter.bukkit.ElixirPresentation.refresh(meta,finalData);items[2]=items[0].clone();items[2].setItemMeta(meta);items[0]=null;use(items,1,consumed);
  }
  // Stored output + consumed inputs + deadline are one CAS-protected chunk snapshot. Recovery never recomputes or recharges.
  return new Work(items,new SpecialistState(t,true,Math.addExact(now,10000),state.selection()));
 }
}
