package me.arcanebrewery.arcaneBrewery.application.fermentation;

import me.arcanebrewery.arcaneBrewery.application.distillery.DistilleryProcessDraft;
import me.arcanebrewery.arcaneBrewery.domain.process.FermentationProfile;

import java.util.Objects;

public record FermentedProcessDraft(
        DistilleryProcessDraft distillery,
        FermentationProfile fermentation
) {
    public FermentedProcessDraft {
        Objects.requireNonNull(distillery, "distillery");
        Objects.requireNonNull(fermentation, "fermentation");
    }
}
