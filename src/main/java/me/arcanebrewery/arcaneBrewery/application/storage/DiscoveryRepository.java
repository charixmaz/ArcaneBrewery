package me.arcanebrewery.arcaneBrewery.application.storage;

import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Aggregate reads are trusted application operations; use projections for player-facing queries. */
public interface DiscoveryRepository {
    Optional<StoredDiscovery> findByFingerprint(DrinkFingerprint fingerprint);
    List<StoredDiscovery> findOwnedBy(UUID owner, int limit, int offset);
    boolean exists(DrinkFingerprint fingerprint);
    RegistrationResult insert(PlayerDiscovery discovery);
    StoredDiscovery update(long expectedRevision, PlayerDiscovery discovery);
    Optional<DiscoverySummary> findPublicSummary(DrinkFingerprint fingerprint);
    List<DiscoverySummary> findPublicSummaries(int limit, int offset);
    RecipeKnowledge knowledgeFor(DrinkFingerprint fingerprint, UUID player);
    default Optional<StoredDiscovery> findForPlayer(DrinkFingerprint id, UUID player) { return findByFingerprint(id); }
    default String recordInfo(DrinkFingerprint id, UUID player) { return "Legacy discovery"; }
    default java.util.List<me.arcanebrewery.arcaneBrewery.application.discovery.OwnedDiscoveryView> personalCatalog(UUID player,int limit,int offset) {
        return findOwnedBy(player,limit,offset).stream().map(s->me.arcanebrewery.arcaneBrewery.application.discovery.OwnedDiscoveryView.from(s.discovery())).toList();
    }
    default java.util.List<DrinkFingerprint> accessibleIds(UUID player) {
        var result=new java.util.ArrayList<DrinkFingerprint>();
        for(int offset=0;;offset+=200) {
            var page=findOwnedBy(player,200,offset);
            page.forEach(s->result.add(s.discovery().fingerprint()));
            if(page.size()<200) return java.util.List.copyOf(result);
        }
    }
    default Optional<String> personalName(UUID player, DrinkFingerprint id) { return findForPlayer(id,player)
        .filter(s->s.discovery().discovererId().equals(player)).map(s->s.discovery().displayName().value()); }
    default RegistrationResult recordProduction(UUID player, me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile process,
        java.time.Instant at,double quality,String key) { return insert(PlayerDiscovery.discover(player,process,at)); }
    default RegistrationResult recordProduction(UUID player, me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile process,
        java.time.Instant at,double quality,String key,byte[] evidence) { return recordProduction(player,process,at,quality,key); }
    // Deliberately no deletion API or ownership-transfer operation.
}
