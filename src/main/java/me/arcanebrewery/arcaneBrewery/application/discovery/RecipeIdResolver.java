package me.arcanebrewery.arcaneBrewery.application.discovery;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.*;
/** No list truncation, guessing, or implicit global recipe lookup. */
public final class RecipeIdResolver {
    private RecipeIdResolver() { }
    public static DrinkFingerprint resolve(Collection<DrinkFingerprint> accessible,String prefix) {
        String value=prefix.strip().toLowerCase(Locale.ROOT);
        if(!value.matches("[0-9a-f]{6,64}")) throw new IllegalArgumentException("Recipe ID must contain 6..64 hexadecimal characters");
        var matches=accessible.stream().filter(id->id.value().startsWith(value)).distinct().toList();
        if(matches.size()!=1) throw new IllegalArgumentException(matches.isEmpty()?"Unknown recipe ID":"Ambiguous recipe ID; type more characters");
        return matches.getFirst();
    }
}
