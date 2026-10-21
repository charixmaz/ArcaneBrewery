package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Presentation is never parsed as identity. Only our recorded lore block is replaced. */
public final class IngredientPresentation {
    private final IngredientMetadataCodec metadata;
    public IngredientPresentation(IngredientMetadataCodec metadata) { this.metadata = Objects.requireNonNull(metadata); }

    public void apply(ItemMeta meta, IngredientDefinition definition, IngredientInstance ingredient,
            boolean washed, int previousPurity) {
        var lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.getLore()) : List.<String>of());
        var key = metadata.key("ingredient_lore");
        String oldBlock = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (oldBlock != null) removeBlock(lore, Arrays.asList(oldBlock.split("\\n", -1)));
        else removeBlock(lore, washedLines(previousPurity));
        if (definition.premium()) removeBlock(lore, List.of(
                "§7A heavy, shimmering grain highly valued by brewmasters.",
                "§7Required to ferment §a4-Star §7to §e5-Star §7beverages.",
                "§dHas a volatile 1% chance to yield legendary 6-Star Mythic alcohol!"));
        var block = new ArrayList<String>();
        block.add("§7Ingredient: §f" + definition.displayName());
        block.addAll(washedLines(ingredient.purity()));
        if (definition.premium()) block.add("§bPremium brewer ingredient");
        lore.addAll(block);
        meta.setDisplayName((washed ? "§aWashed " : definition.premium() ? "§b§l" : "§f") + definition.displayName());
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, String.join("\n", block));
    }

    private List<String> washedLines(int purity) {
        String color = purity == 100 ? "§2" : purity >= 50 ? "§e" : "§c";
        return List.of("§7Status: " + color + (purity == 100 ? "Fully Cleaned" : "Partially Washed"),
                "§7Purity Level: " + color + purity + "%", "",
                purity < 100 ? "§8§oRequires more washing cycles." : "§d§oReady for industrial refinery processing lines.");
    }

    private void removeBlock(List<String> lore, List<String> block) {
        int start = Collections.indexOfSubList(lore, block);
        if (start >= 0) lore.subList(start, start + block.size()).clear();
    }
}
