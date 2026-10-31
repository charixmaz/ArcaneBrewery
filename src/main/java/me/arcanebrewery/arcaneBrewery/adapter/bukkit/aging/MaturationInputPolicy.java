package me.arcanebrewery.arcaneBrewery.adapter.bukkit.aging;

import me.arcanebrewery.arcaneBrewery.adapter.bukkit.alchemy.AlchemyPreparationMetadata;
import me.arcanebrewery.arcaneBrewery.adapter.bukkit.fermentation.FermentationBatchMetadata;
import me.arcanebrewery.arcaneBrewery.application.fermentation.FermentationBatchState;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;

/** Only completed canonical cultivation can start maturation. */
public final class MaturationInputPolicy {
    private final String namespace;
    public MaturationInputPolicy(String namespace) { this.namespace=Objects.requireNonNull(namespace); }
    /** Called on a copy; completion is committed only with the accepted slot edit. */
    public Optional<FermentationBatchState> completedSource(ItemMeta meta,long now) {
        new AlchemyPreparationMetadata(namespace).read(meta);
        var metadata=new FermentationBatchMetadata(namespace);
        var stored=metadata.read(meta);
        if (stored.isEmpty()) throw new IllegalArgumentException("a completed cultivated base is required");
        var state=stored.get();
        if (state.status()==FermentationBatchState.Status.FERMENTING && state.ready(now)) {
            state=state.complete(now); metadata.write(meta,state);
        }
        if (state.status()!=FermentationBatchState.Status.COMPLETED) throw new IllegalArgumentException("cultivation is incomplete");
        return Optional.of(state);
    }
}
