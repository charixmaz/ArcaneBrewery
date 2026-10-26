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
        String name = meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : MessageConfig.text("names.elixir");
        if (name.equals("Undermatured Cask Batch")) name = MessageConfig.text("names.unfinished");
        if (name.equals("Finished Drink")) name = MessageConfig.text("names.elixir");
        meta.setDisplayName("§d§l" + name + "§r");
        var lore = new ArrayList<String>();
        if(data.producer(meta).isPresent()) lore.add("§7§oBrewed by "+data.producerLabel(meta)+"§r");
        boolean unfinished=data.unfinished(meta)||name.equals(MessageConfig.text("names.unfinished"));
        String state=unfinished?"§eUnfinished":elixir.quality()==0?"§cRuined":"§aFinished";
        lore.add("§7Quality §f"+Math.round(elixir.quality())+"/100 §8· "+state+"§r");
        data.readAlchemy(meta).filter(a->a.effect()!=me.arcanebrewery.arcaneBrewery.application.alchemy.AlchemyEffect.NONE)
            .ifPresent(a->lore.add("§7Effect §d"+PlayerConditionPolicy.readable(a.effect().name())+"§r"));
        lore.add("§7Load §b"+Math.round(elixir.strength())+" §8· §7Contamination §f"+Math.round(elixir.contamination())+"§r");
        data.maturityTarget(meta).ifPresent(t->lore.add("§7Ideal §f"+t+"–"+(t+15)+"s"
            +data.harvestSeconds(meta).map(h->" §8· §7Taken §f"+h+"s").orElse("")+"§r"));
        if(data.prematurePoison(meta)) lore.add("§cPoisonous if consumed.§r");
        else if(unfinished) lore.add("§eWithdrawn early; no discovery.§r");
        if(!unfinished) data.readValue(meta).ifPresent(v->lore.add("§7Value §6"+Math.round(v.units())+" units§r"));
        lore.add("§8/brew inspect — full details§r");
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
