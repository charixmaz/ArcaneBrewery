package me.arcanebrewery.arcaneBrewery.domain.ingredient;

import java.util.Objects;

public record IngredientDefinition(IngredientProfile profile, String displayName, boolean premium,
        IngredientModifiers defaultModifiers) {
    public IngredientDefinition {
        Objects.requireNonNull(profile);
        String id = IngredientIds.canonical(profile.id());
        profile = new IngredientProfile(id, profile.sugar(), profile.starch(), profile.acidity(),
                profile.bitterness(), profile.aroma(), profile.toxicity(), profile.magicalAffinity());
        Objects.requireNonNull(displayName);
        if (displayName.isBlank() || displayName.length() > 80 || displayName.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid ingredient display name");
        Objects.requireNonNull(defaultModifiers);
    }
    public String id() { return profile.id(); }
}
