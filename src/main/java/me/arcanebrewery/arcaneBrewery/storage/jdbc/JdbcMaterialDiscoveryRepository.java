package me.arcanebrewery.arcaneBrewery.storage.jdbc;
import me.arcanebrewery.arcaneBrewery.application.discovery.*;
import java.util.*;
import java.time.Instant;
/** A unique player/kind/stable-ID key arbitrates one-time notifications across sessions and restarts. */
public final class JdbcMaterialDiscoveryRepository implements MaterialDiscoveryRepository {
    private final JdbcConnectionFactory connections;
    public JdbcMaterialDiscoveryRepository(JdbcConnectionFactory connections) {this.connections=Objects.requireNonNull(connections);}
    public Update obtain(UUID player,Set<MaterialKey> keys,Instant at) {
        Objects.requireNonNull(player);Objects.requireNonNull(at);keys=Set.copyOf(keys);if(keys.size()>128) throw new IllegalArgumentException("too many materials");
        final var snapshot=keys;
        return JdbcTransactions.run(connections,true,c->{
            var added=new HashSet<MaterialKey>();
            try(var q=c.prepareStatement("INSERT OR IGNORE INTO material_discoveries(player_id,kind,registry_id,obtained_at) VALUES (?,?,?,?)")) {
                for(var key:snapshot) {q.setString(1,player.toString());q.setString(2,key.kind().name());q.setString(3,key.registryId());q.setString(4,at.toString());if(q.executeUpdate()==1) added.add(key);}
            }
            var known=new HashSet<MaterialKey>();try(var q=c.prepareStatement("SELECT kind,registry_id FROM material_discoveries WHERE player_id=?")) {
                q.setString(1,player.toString());try(var rs=q.executeQuery()) {while(rs.next()) known.add(new MaterialKey(MaterialKey.Kind.valueOf(rs.getString(1)),rs.getString(2)));}
            }return new Update(added,known);
        });
    }
}
