package me.arcanebrewery.arcaneBrewery.application.alchemy;
import java.util.*;
public final class BuiltInAlchemyCatalysts {
    private BuiltInAlchemyCatalysts() { }
    private static final Map<String,AlchemyCatalyst> BY_EXTERNAL=ReagentRegistry.all().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ReagentDefinition::externalId,ReagentDefinition::catalyst));
    public static Optional<AlchemyCatalyst> resolveExternal(String externalId) { return Optional.ofNullable(BY_EXTERNAL.get(externalId)); }
    public static Map<String,AlchemyCatalyst> all() { return BY_EXTERNAL; }
}
