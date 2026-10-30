package me.arcanebrewery.arcaneBrewery.adapter.bukkit;

import me.arcanebrewery.arcaneBrewery.MessageConfig;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging.FinalDrinkMetadata;
import me.arcanebrewery.arcaneBrewery.application.consumption.PlayerConditionPolicy;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;

/** Presentation-only refresh: does not touch PDC, quantity or potion effects. */
public final class ElixirPresentation {
    private ElixirPresentation() { }
    public static boolean refresh(ItemMeta meta, FinalDrinkMetadata data) {
        var profile = data.readConsumption(meta);
        if (profile.isEmpty()) return false;
        var elixir = profile.orElseThrow();
        String name = meta.hasDisplayName() ? meta.getDisplayName() : MessageConfig.text("names.elixir");
        name=name.replaceFirst("^§d§l", "").replaceFirst("§r$", "");
        if (name.equals("Undermatured Cask Batch")) name = MessageConfig.text("names.unfinished");
        if (name.equals("Finished Drink")) name = MessageConfig.text("names.elixir");
        meta.setDisplayName("§d§l" + name + "§r");
        var lore = new ArrayList<String>();
        if(data.producer(meta).isPresent()) lore.add("§7§oBrewed by "+data.producerLabel(meta)+"§r");
        boolean unfinished=data.unfinished(meta)||name.equals(MessageConfig.text("names.unfinished"));
        String state=unfinished?"§eUnfinished":elixir.quality()==0?"§cRuined":"§aFinished";
        lore.add("§7Quality §f"+Math.round(elixir.quality())+"/100 §8· "+state+"§r");
        var effects=data.readEffects(meta);
        if(effects.isPresent()) {
            String brief=effects.get().effects().stream().filter(e->!e.sideEffect()).map(e->PlayerConditionPolicy.readable(e.effect().name())+" "+(e.amplifier()+1)+" · "+e.seconds()+"s").collect(java.util.stream.Collectors.joining(" + "));
            if(!brief.isEmpty())lore.add("§7Effects §d"+brief+"§r");
            String drawbacks=effects.get().effects().stream().filter(e->e.sideEffect()).map(e->PlayerConditionPolicy.readable(e.effect().name())).collect(java.util.stream.Collectors.joining(", "));
            if(!drawbacks.isEmpty())lore.add("§eSide effect: "+drawbacks+"§r");
            if(effects.get().safety()==me.arcanebrewery.arcaneBrewery.application.alchemy.EffectProfile.Safety.DANGEROUS)lore.add("§cDangerous reaction — inspect before use.§r");
        } else data.readAlchemy(meta).filter(a->a.effect()!=me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyEffect.NONE)
            .ifPresent(a->lore.add("§7Effect §d"+PlayerConditionPolicy.readable(a.effect().name())+"§r"));
        if(effects.isEmpty()) lore.add("§7Load §b"+Math.round(elixir.strength())+"§r");
        if(effects.isEmpty()) data.harvestSeconds(meta).ifPresent(h->lore.add("§7Harvested at §f"+h+"s§r"));
        if(data.prematurePoison(meta)) lore.add("§cPoisonous if consumed.§r");
        else if(unfinished) lore.add("§eWithdrawn early; no discovery.§r");
        TastingNotes.read(meta).ifPresent(note->lore.add("§7Taste §f"+note+"§r"));
        lore.add("§8/al inspect — full details§r");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        PresentationStyle.apply(meta);
        if(data.producer(meta).isPresent() && meta.lore()!=null) {
            var styled=new ArrayList<>(meta.lore());
            styled.set(0,net.kyori.adventure.text.Component.text("Brewed by "+data.producerLabel(meta),net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,true));
            meta.lore(styled);
        }
        return true;
    }
}
