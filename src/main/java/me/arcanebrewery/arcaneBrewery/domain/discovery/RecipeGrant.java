package me.arcanebrewery.arcaneBrewery.domain.discovery;

import java.util.Set;

/** Snapshot of an explicit grant; details are derived from the immutable discovery process. */
public record RecipeGrant(boolean full, Set<ProcessField> fields) {
    public RecipeGrant {
        fields = Set.copyOf(fields);
        if (full ? !fields.isEmpty() : fields.isEmpty() || fields.size() == ProcessField.values().length)
            throw new IllegalArgumentException("invalid grant selection");
    }
    public static RecipeGrant fullRecipe() { return new RecipeGrant(true, Set.of()); }
    public static RecipeGrant partial(Set<ProcessField> fields) { return new RecipeGrant(false, fields); }
}
