package me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy;

import me.arcanebrewery.arcaneBrewery.application.alchemy.*;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import java.util.*;

/** Shared validation for live starts and persisted PROCESSING recovery. */
public final class AlchemyMachineInputs {
    private AlchemyMachineInputs() {}
    public static List<AlchemyCatalyst> catalysts(ItemStack[] inputs) {
        var result = new ArrayList<AlchemyCatalyst>();
        for (int slot : new int[]{1, 2}) {
            var item = inputs[slot];
            if (AlchemyMachineItems.empty(item)) continue;
            if (item.getAmount() > item.getMaxStackSize()) throw new IllegalArgumentException("invalid catalyst quantity");
            result.add(BuiltInAlchemyCatalysts.resolveExternal(item.getType().getKey().toString())
                    .orElseThrow(() -> new IllegalArgumentException("unsupported catalyst")));
        }
        return List.copyOf(result);
    }
    public static AlchemyMachineOperation start(ItemStack[] inputs, String namespace, long now, long duration) {
        var source = source(inputs, namespace);
        if (new AlchemyPreparationMetadata(namespace).read(inputs[0].getItemMeta()).isPresent())
            throw new IllegalArgumentException("base is already infused");
        var service = new AlchemyMachineService();
        return new AlchemyMachineOperation(service.start(now, duration, UUID.randomUUID()), source.batchId(),
                service.combine(catalysts(inputs)));
    }
    public static void validateLocked(ItemStack[] inputs, String namespace, AlchemyMachineOperation operation) {
        if (!source(inputs, namespace).batchId().equals(operation.sourceBatch()))
            throw new IllegalArgumentException("locked base does not match operation");
        if (new AlchemyPreparationMetadata(namespace).read(inputs[0].getItemMeta()).isPresent()
                || !new AlchemyMachineService().combine(catalysts(inputs)).equals(operation.preparation()))
            throw new IllegalArgumentException("locked inputs do not match operation");
    }
    public static void validateCompleted(ItemStack[] inputs, String namespace, AlchemyMachineOperation operation) {
        if (operation.state().status() != AlchemyMachineState.Status.COMPLETED
                || !source(inputs, namespace).batchId().equals(operation.sourceBatch())
                || !new AlchemyPreparationMetadata(namespace).read(inputs[0].getItemMeta())
                        .map(operation.preparation()::equals).orElse(false))
            throw new IllegalArgumentException("completed output does not match operation");
    }
    private static FermentationBatchState source(ItemStack[] inputs, String namespace) {
        var base = inputs[0];
        if (AlchemyMachineItems.empty(base) || base.getType() != Material.POTION || base.getAmount() != 1
                || !(base.getItemMeta() instanceof PotionMeta meta))
            throw new IllegalArgumentException("one completed fermented base is required");
        var source = new FermentationBatchMetadata(namespace).read(meta).orElseThrow(
                () -> new IllegalArgumentException("base is not canonical"));
        if (source.status() != FermentationBatchState.Status.COMPLETED)
            throw new IllegalArgumentException("base fermentation is incomplete");
        return source;
    }
    public static ItemStack[] complete(ItemStack[] inputs, String namespace, AlchemyMachineOperation operation, long now) {
        if (operation.state().status() != AlchemyMachineState.Status.PROCESSING || !operation.state().ready(now))
            throw new IllegalStateException("machine is not ready to complete");
        validateLocked(inputs, namespace, operation);
        ItemStack[] output = Arrays.stream(inputs).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        var meta = Objects.requireNonNull(output[0].getItemMeta());
        new AlchemyPreparationMetadata(namespace).write(meta, operation.preparation());
        var lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.<String>of());
        lore.add("§dArcane Infusion: §f" + operation.preparation().additives().size() + " catalyst type(s)");
        meta.setLore(lore);
        if (!output[0].setItemMeta(meta)) throw new IllegalStateException("unable to prepare base");
        for (int slot : new int[]{1, 2}) {
            if (AlchemyMachineItems.empty(output[slot])) continue;
            if (output[slot].getAmount() == 1) output[slot] = null;
            else output[slot].setAmount(output[slot].getAmount() - 1);
        }
        return output;
    }

    public static ItemStack[] fail(ItemStack[] inputs, String namespace,
            AlchemyMachineOperation operation, long now, AlchemyFailureOutcome outcome) {
        if (operation.state().status() != AlchemyMachineState.Status.PROCESSING || !operation.state().ready(now))
            throw new IllegalStateException("machine is not ready to fail");
        if (outcome != AlchemyFailureOutcome.FAILED_INFUSION
                && outcome != AlchemyFailureOutcome.CRITICAL_FAILURE)
            throw new IllegalArgumentException("failure outcome required");
        validateLocked(inputs, namespace, operation);
        ItemStack[] output = Arrays.stream(inputs).map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
        for (int slot : new int[]{1, 2}) {
            if (AlchemyMachineItems.empty(output[slot])) continue;
            if (output[slot].getAmount() == 1) output[slot] = null;
            else output[slot].setAmount(output[slot].getAmount() - 1);
        }
        if (outcome == AlchemyFailureOutcome.CRITICAL_FAILURE) {
            output[0] = null;
        } else {
            var meta = Objects.requireNonNull(output[0].getItemMeta());
            var lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null
                    ? meta.getLore() : List.<String>of());
            lore.add("§8Arcane residue: §cFailed infusion");
            meta.setLore(lore);
            if (!output[0].setItemMeta(meta))
                throw new IllegalStateException("unable to mark failed infusion");
        }
        return output;
    }
}
