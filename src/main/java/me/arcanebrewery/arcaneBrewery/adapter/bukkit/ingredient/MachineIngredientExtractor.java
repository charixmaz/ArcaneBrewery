package me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient;

import me.arcanebrewery.arcaneBrewery.application.ingredient.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Read-only V1.5 bridge. Callers select input slots, excluding GUI decorations/fuel/output slots. */
public final class MachineIngredientExtractor {
    private final IngredientItemAdapter items;
    private final BatchIngredientExtractor extractor;
    public MachineIngredientExtractor(IngredientItemAdapter items) {
        this.items = Objects.requireNonNull(items);
        this.extractor = new BatchIngredientExtractor(items.registry());
    }
    public ExtractedIngredients extract(ItemStack[] contents) {
        var inputs = new ArrayList<IngredientInput>();
        for (var item : contents) inputs.add(items.classify(item));
        return extractor.extract(inputs);
    }
    public ExtractedIngredients extract(Inventory inventory, int... slots) {
        var selected = new TreeSet<Integer>();
        for (int slot : slots) {
            if (slot < 0 || slot >= inventory.getSize() || !selected.add(slot))
                throw new IllegalArgumentException("invalid or duplicate ingredient input slot");
        }
        return extract(selected.stream().map(inventory::getItem).toArray(ItemStack[]::new));
    }
}
