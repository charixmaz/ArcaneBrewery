package me.arcanebrewery.arcaneBrewery;

import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.ingredient.IngredientItemAdapter;
import me.arcanebrewery.arcaneBrewery.application.ingredient.BuiltInIngredients;
import me.arcanebrewery.arcaneBrewery.domain.ingredient.*;
import java.util.Optional;
import java.util.Random;

public class AgricultureListener implements Listener {

    private final IngredientItemAdapter ingredients;
    private final Random random = new Random();

    public AgricultureListener(IngredientItemAdapter ingredients) {
        this.ingredients = java.util.Objects.requireNonNull(ingredients);
    }

    @EventHandler
    public void onCropHarvest(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.WHEAT) return;

        if (event.getBlock().getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;

            Player player = event.getPlayer();

            if (random.nextDouble() <= 0.02) {
                ItemStack specialGrain = new ItemStack(Material.WHEAT, 1);
                if (specialGrain.getItemMeta() != null) {
                    var definition = ingredients.registry().require(BuiltInIngredients.PREMIUM_GRAIN);
                    var provenance = new IngredientProvenance("arcanebrewery:wheat_harvest", Optional.of(player.getUniqueId()));
                    specialGrain = ingredients.write(specialGrain, new IngredientInstance(definition.id(), 0, 1,
                            definition.defaultModifiers(), Optional.of(provenance)), false);

                    event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), specialGrain);
                    player.sendMessage("§d§l[ArcaneBrewery] §7You discovered a rare §b§lExclusive Brewer's Grain§7!");
                }
            }
        }
    }
}
