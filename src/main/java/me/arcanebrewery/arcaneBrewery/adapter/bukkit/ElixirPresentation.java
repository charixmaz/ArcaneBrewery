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
        data.wood(meta).ifPresent(wood->lore.add("§7Maturation: §f"+wood+"§r"));
        lore.add("§7Quality: §f" + Math.round(elixir.quality()) + "/100§r");
        lore.add("§7Arcane Load: §b" + Math.round(elixir.strength()) + "/100§r");
        lore.add("§8Higher load increases temporary saturation.§r");
        lore.add("§7Magical Potency: §d" + Math.round(elixir.magicalPotency()) + "/100§r");
        lore.add("§7Contamination: §f" + Math.round(elixir.contamination()) + "/100§r");
        data.readAlchemy(meta).ifPresent(alchemy -> {
            lore.add("§7Alchemy: §d" + PlayerConditionPolicy.readable(alchemy.effect().name()) + "§r");
            lore.add("§7Instability: §f" + Math.round(alchemy.instability()) + "/100§r");
        });
        data.readValue(meta).ifPresent(value -> lore.add("§7Calculated Value: §6" + Math.round(value.units()) + " units§r"));
        if (name.equals(MessageConfig.text("names.unfinished"))) lore.add("§eWithdrawn early; no recipe discovery.§r");
        lore.add("§7Right-click to use this elixir.§r");
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
