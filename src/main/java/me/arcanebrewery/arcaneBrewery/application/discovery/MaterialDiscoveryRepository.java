package me.arcanebrewery.arcaneBrewery.application.discovery;
import java.util.*;
import java.time.Instant;
public interface MaterialDiscoveryRepository {
    record Update(Set<MaterialKey> newlyUnlocked,Set<MaterialKey> known) {
        public Update {newlyUnlocked=Set.copyOf(newlyUnlocked);known=Set.copyOf(known);}
    }
    Update obtain(UUID player,Set<MaterialKey> actualPossession,Instant at);
}
