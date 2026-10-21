package me.arcanebrewery.arcaneBrewery.storage.codec;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import java.util.EnumSet;
import java.util.stream.Collectors;

/** V1 field vocabulary uses ProcessField identifiers, not Java class names. Values come from the process. */
public final class RecipeGrantCodec {
    public String encode(RecipeGrant grant) {
        if (grant.full()) return null;
        return "1:" + grant.fields().stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    public RecipeGrant decode(String kind, String payload) {
        if ("FULL".equals(kind)) {
            if (payload != null) throw new CorruptRecordException("Full grant has partial payload");
            return RecipeGrant.fullRecipe();
        }
        if (!"PARTIAL".equals(kind) || payload == null || payload.length() > 512)
            throw new CorruptRecordException("Invalid grant payload");
        if (!payload.startsWith("1:")) throw new UnsupportedStorageVersionException("Unsupported grant codec version");
        try {
            var fields = EnumSet.noneOf(ProcessField.class);
            for (String field : payload.substring(2).split(",", -1)) {
                if (!fields.add(ProcessField.valueOf(field))) throw new IllegalArgumentException("duplicate field");
            }
            var grant = RecipeGrant.partial(fields);
            if (!payload.equals(encode(grant))) throw new IllegalArgumentException("noncanonical grant");
            return grant;
        } catch (IllegalArgumentException e) {
            throw new CorruptRecordException("Malformed partial grant payload", e);
        }
    }
}
