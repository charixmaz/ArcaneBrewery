package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.Objects;
import java.util.UUID;

/** Recipient-specific data projections. Only FULL contains an exact process. */
public sealed interface RecipeKnowledge permits RecipeKnowledge.Unknown, RecipeKnowledge.Identified,
        RecipeKnowledge.Partial, RecipeKnowledge.Full {
    enum State { UNKNOWN, IDENTIFIED, PARTIAL, FULL }
    UUID playerId();
    State state();

    record Unknown(UUID playerId) implements RecipeKnowledge {
        public Unknown { Objects.requireNonNull(playerId, "playerId"); }
        public State state() { return State.UNKNOWN; }
    }

    record Identified(UUID playerId, DiscoverySummary discovery) implements RecipeKnowledge {
        public Identified {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(discovery, "discovery");
        }
        public State state() { return State.IDENTIFIED; }
    }

    record Partial(UUID playerId, DiscoverySummary discovery, RecipeDisclosure disclosure) implements RecipeKnowledge {
        public Partial {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(discovery, "discovery");
            Objects.requireNonNull(disclosure, "disclosure");
        }
        public State state() { return State.PARTIAL; }
    }

    record Full(UUID playerId, DiscoverySummary discovery, ProcessProfile process) implements RecipeKnowledge {
        public Full {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(discovery, "discovery");
            Objects.requireNonNull(process, "process");
            if (!DrinkFingerprint.from(process).equals(discovery.fingerprint())
                    && !me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity.from(process).equals(discovery.fingerprint()))
                throw new IllegalArgumentException("full knowledge must reproduce the discovery fingerprint");
        }
        public State state() { return State.FULL; }
    }
}
