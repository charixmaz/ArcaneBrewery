package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Presentation is never parsed as identity. Only our recorded lore block is replaced. */
public final class IngredientPresentation {
    private static boolean historicPremiumLore(String line) {
        try {
            var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return java.util.Set.of("ae95c8e1f1b4f3e8ffe3625b178b9a079f0c8d9077c473f4c215d18b8aa518e2","78be393739b669bc1416855aec24be51d294c2d8673b131f8f875376eb21f2fd","9babb41ec73069b0c5776ab49d1de94ddb942dcce79680ce14d8f3aba6fc382c").contains(hash);
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private final IngredientMetadataCodec metadata;
    public IngredientPresentation(IngredientMetadataCodec metadata) { this.metadata = Objects.requireNonNull(metadata); }

    public void apply(ItemMeta meta, IngredientDefinition definition, IngredientInstance ingredient,
            boolean washed, int previousPurity) {
        var lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.getLore()) : List.<String>of());
        var key = metadata.key("ingredient_lore");
        String oldBlock = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (oldBlock != null) removeBlock(lore, Arrays.asList(oldBlock.split("\\n", -1)));
        else { removeBlock(lore, washedLines(previousPurity)); removeBlock(lore,legacyWashedLines(previousPurity)); }
        if (definition.premium()) lore.removeIf(line -> historicPremiumLore(line));
        var block = new ArrayList<String>();
        block.add("§7Ingredient: §f" + definition.displayName());
        block.addAll(washedLines(ingredient.purity()));
        if (definition.premium()) block.add("§bPremium brewer ingredient");
        lore.addAll(block);
        meta.setDisplayName((washed ? "§a§lWashed " : definition.premium() ? "§b§l" : "§f§l") + definition.displayName()+"§r");
        meta.setLore(lore);
        me.arcanebrewery.arcaneBrewery.adapter.bukkit.PresentationStyle.apply(meta);
        // Adventure may normalize legacy reset codes. Track the actual rendered
        // block so the next wash removes it exactly without duplicating stats.
        var rendered=Objects.requireNonNull(meta.getLore());
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                String.join("\n",rendered.subList(rendered.size()-block.size(),rendered.size())));
    }

    private List<String> washedLines(int purity) {
        String color = purity == 100 ? "§2" : purity >= 50 ? "§e" : "§c";
        return List.of("§7Status: " + color + (purity == 100 ? "Fully Cleaned" : "Partially Washed"),
                "§7Purity Level: " + color + purity + "%", "",
                purity < 100 ? "§7Requires more washing cycles.§r" : "§7Ready for essence extraction.§r");
    }
    private List<String> legacyWashedLines(int purity) {
        String color=purity==100?"§2":purity>=50?"§e":"§c";
        return List.of("§7Status: "+color+(purity==100?"Fully Cleaned":"Partially Washed"),"§7Purity Level: "+color+purity+"%","",
                purity<100?"§8§oRequires more washing cycles.":"§d§oReady for industrial refinery processing lines.");
    }

    private void removeBlock(List<String> lore, List<String> block) {
        int start = Collections.indexOfSubList(lore, block);
        if (start >= 0) lore.subList(start, start + block.size()).clear();
    }
}
