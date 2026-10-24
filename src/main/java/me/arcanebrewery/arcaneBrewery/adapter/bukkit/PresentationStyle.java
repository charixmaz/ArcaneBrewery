package me.arcanebrewery.arcaneBrewery.adapter.bukkit;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

/** Explicitly overrides Minecraft's implicit italic custom-name/lore styling.
 * Deliberate §o flavour spans still override this inherited default. */
public final class PresentationStyle {
    private PresentationStyle() { }
    public static void apply(ItemMeta meta) {
        var serializer=LegacyComponentSerializer.legacySection();
        if (meta.hasDisplayName()) meta.displayName(defaultStyle(serializer.deserialize(meta.getDisplayName())));
        if (meta.hasLore() && meta.getLore()!=null) meta.lore(meta.getLore().stream()
                .map(line -> defaultStyle(serializer.deserialize(line))).toList());
    }
    private static net.kyori.adventure.text.Component defaultStyle(net.kyori.adventure.text.Component component) {
        return component.decoration(TextDecoration.ITALIC)==TextDecoration.State.NOT_SET
                ? component.decoration(TextDecoration.ITALIC,false):component;
    }
}
