package me.arcanebrewery.arcaneBrewery.storage.jdbc;

import me.arcanebrewery.arcaneBrewery.application.storage.*;
import me.arcanebrewery.arcaneBrewery.domain.discovery.*;
import me.arcanebrewery.arcaneBrewery.domain.drink.*;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import me.arcanebrewery.arcaneBrewery.storage.codec.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Player-scoped V2 aggregates. Legacy tables are retained verbatim and never used as a public process catalog. */
public final class PersonalDiscoveryRepository implements DiscoveryRepository {
    private final JdbcConnectionFactory connections;
    private final DiscoveryRepository legacy;
    private final ProcessProfileCodec codec=new ProcessProfileCodec();
    private final RecipeGrantCodec grantCodec=new RecipeGrantCodec();
    public PersonalDiscoveryRepository(JdbcConnectionFactory connections,DiscoveryRepository legacy) {
        this.connections=connections;this.legacy=legacy;migrate();
    }
    private static int execute(Connection c,String sql,Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql)) { bind(s,args);return s.executeUpdate(); }
    }
    private static void bind(PreparedStatement s,Object...args) throws SQLException {
        for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
    }
    private void migrate() {
        JdbcTransactions.run(connections,true,c->{
            try(var s=c.createStatement();var rs=s.executeQuery("SELECT 1 FROM gameplay_migrations WHERE version=1")) { if(rs.next()) return null; }
            var ids=new ArrayList<DrinkFingerprint>();
            try(var s=c.createStatement();var rs=s.executeQuery("SELECT fingerprint FROM discoveries ORDER BY discovered_at,owner_id,fingerprint")) {
                while(rs.next()) ids.add(new DrinkFingerprint(rs.getString(1)));
            }
            // Sorting Instants, UUIDs, then old IDs is deterministic even for timestamp offsets.
            var old=new ArrayList<StoredDiscovery>();
            for(var id:ids) old.add(legacy.findByFingerprint(id).orElseThrow());
            old.sort(Comparator.comparing((StoredDiscovery s)->s.discovery().discoveredAt())
                .thenComparing(s->s.discovery().discovererId().toString()).thenComparing(s->s.discovery().fingerprint().value()));
            for(var stored:old) {
                var d=stored.discovery();var id=FormulationIdentity.from(d.process());
                execute(c,"INSERT OR IGNORE INTO recipe_aliases VALUES (?,?)",d.fingerprint().value(),id.value());
                insertRecord(c,id,d.discovererId(),d.process(),d.discoveredAt(),d.temporaryName().value(),
                    d.canonicalName().map(DrinkName::value).orElse(null),d.visibility(),"INDEPENDENT",true);
                execute(c,"INSERT OR IGNORE INTO server_firsts VALUES (?,?,?)",id.value(),d.discovererId().toString(),d.discoveredAt().toString());
                for(var g:d.snapshot().grants().entrySet()) {
                    insertRecord(c,id,g.getKey(),null,d.discoveredAt(),d.temporaryName().value(),null,DiscoveryVisibility.PRIVATE,"LEARNED",false);
                    execute(c,"INSERT OR IGNORE INTO personal_grants(identity,source_id,recipient_id,kind,selection_payload,legacy_source) VALUES (?,?,?,?,?,?)",id.value(),d.discovererId().toString(),g.getKey().toString(),
                        g.getValue().full()?"FULL":"PARTIAL",grantCodec.encode(g.getValue()),d.fingerprint().value());
                }
            }
            execute(c,"INSERT INTO gameplay_migrations VALUES (1)");return null;
        });
    }
    private int insertRecord(Connection c,DrinkFingerprint id,UUID player,ProcessProfile process,Instant time,String temporary,
            String name,DiscoveryVisibility visibility,String provenance,boolean produced) throws SQLException {
        return execute(c,"INSERT OR IGNORE INTO personal_recipes(identity,player_id,process_payload,recorded_at,temporary_name,personal_name,visibility,provenance,produced) VALUES (?,?,?,?,?,?,?,?,?)",
            id.value(),player.toString(),process==null?null:codec.encode(process),time.toString(),temporary,name,visibility.name(),provenance,produced?1:0);
    }
    private DrinkFingerprint resolve(Connection c,DrinkFingerprint id) throws SQLException {
        try(var s=c.prepareStatement("SELECT identity FROM recipe_aliases WHERE legacy_fingerprint=?")) {
            bind(s,id.value());try(var rs=s.executeQuery()) { return rs.next()?new DrinkFingerprint(rs.getString(1)):id; }
        }
    }
    public RegistrationResult insert(PlayerDiscovery candidate) {
        return recordProduction(candidate.discovererId(),candidate.process(),candidate.discoveredAt(),0,
            UUID.randomUUID().toString());
    }
    /** A unique batch key makes retries idempotent. First recognition and personal record commit atomically. */
    public RegistrationResult recordProduction(UUID player,ProcessProfile p,Instant at,double quality,String batchKey) {
        return recordProduction(player,p,at,quality,batchKey,null);
    }
    public RegistrationResult recordProduction(UUID player,ProcessProfile p,Instant at,double quality,String batchKey,byte[] evidence) {
        if(batchKey==null || batchKey.isBlank() || batchKey.length()>128) throw new IllegalArgumentException("invalid batch key");
        if(evidence!=null && evidence.length>1048576) throw new IllegalArgumentException("batch evidence too large");
        if(!Double.isFinite(quality)||quality<0||quality>100) throw new IllegalArgumentException("invalid quality");
        var id=FormulationIdentity.from(p);
        return JdbcTransactions.run(connections,true,c->{
            try(var q=c.prepareStatement("SELECT identity,player_id,process_payload,quality FROM production_batches WHERE batch_key=?")) {
                bind(q,batchKey);try(var rs=q.executeQuery()) {
                    if(rs.next() && (!rs.getString(1).equals(id.value()) || !rs.getString(2).equals(player.toString())
                        || !Arrays.equals(rs.getBytes(3),codec.encode(p)) || rs.getDouble(4)!=quality))
                        throw new IllegalArgumentException("batch key already records a different production attempt");
                }
            }
            int added=insertRecord(c,id,player,p,at,new DescriptiveNameGenerator().generate(p).value(),null,DiscoveryVisibility.PRIVATE,"INDEPENDENT",true);
            execute(c,"INSERT OR IGNORE INTO recipe_aliases VALUES (?,?)",DrinkFingerprint.from(p).value(),id.value());
            execute(c,"INSERT OR IGNORE INTO production_batches VALUES (?,?,?,?,?,?,?)",batchKey,id.value(),player.toString(),codec.encode(p),at.toString(),quality,evidence);
            // Existing learned provenance survives both revocation and subsequent production.
            execute(c,"UPDATE personal_recipes SET process_payload=COALESCE(process_payload,?),produced=1,best_quality=MAX(COALESCE(best_quality,0),?) WHERE identity=? AND player_id=?",
                codec.encode(p),quality,id.value(),player.toString());
            boolean independent;
            try(var s=c.prepareStatement("SELECT provenance FROM personal_recipes WHERE identity=? AND player_id=?")) {
                bind(s,id.value(),player.toString());try(var rs=s.executeQuery()) { rs.next();independent=rs.getString(1).equals("INDEPENDENT"); }
            }
            if(independent) execute(c,"INSERT OR IGNORE INTO server_firsts VALUES (?,?,?)",id.value(),player.toString(),at.toString());
            UUID first=player;
            try(var s=c.prepareStatement("SELECT player_id FROM server_firsts WHERE identity=?")) {
                bind(s,id.value());try(var rs=s.executeQuery()) { if(rs.next()) first=UUID.fromString(rs.getString(1)); }
            }
            return new RegistrationResult(added==1?RegistrationResult.Status.REGISTERED:RegistrationResult.Status.ALREADY_DISCOVERED,id,first);
        });
    }
    public Optional<StoredDiscovery> findForPlayer(DrinkFingerprint requested,UUID player) {
        return JdbcTransactions.run(connections,false,c->load(c,resolve(c,requested),player));
    }
    private Optional<StoredDiscovery> load(Connection c,DrinkFingerprint id,UUID player) throws SQLException {
        try(var s=c.prepareStatement("SELECT * FROM personal_recipes WHERE identity=? AND player_id=?")) {
            bind(s,id.value(),player.toString());try(var rs=s.executeQuery()) {
                if(!rs.next()) return Optional.empty();
                byte[] payload=rs.getBytes("process_payload");
                ProcessProfile process=payload==null?fullGrantedProcess(c,id,player):codec.decode(payload);
                if(process==null) return Optional.empty();
                var grants=new HashMap<UUID,RecipeGrant>();
                try(var q=c.prepareStatement("SELECT recipient_id,kind,selection_payload FROM personal_grants WHERE identity=? AND source_id=?")) {
                    bind(q,id.value(),player.toString());try(var g=q.executeQuery()) { while(g.next()) grants.put(UUID.fromString(g.getString(1)),grantCodec.decode(g.getString(2),g.getString(3))); }
                }
                var visibility=DiscoveryVisibility.valueOf(rs.getString("visibility"));
                if(visibility==DiscoveryVisibility.SHARED && grants.isEmpty()) visibility=DiscoveryVisibility.PRIVATE;
                var snapshot=new DiscoverySnapshot(player,id,process,Instant.parse(rs.getString("recorded_at")),new DrinkName(rs.getString("temporary_name")),
                    Optional.ofNullable(rs.getString("personal_name")).map(DrinkName::new),visibility,grants);
                return Optional.of(new StoredDiscovery(PlayerDiscovery.restore(snapshot),rs.getLong("revision")));
            }
        }
    }
    private ProcessProfile fullGrantedProcess(Connection c,DrinkFingerprint id,UUID recipient) throws SQLException {
        return fullGrantedProcess(c,id,recipient,new HashSet<>());
    }
    private ProcessProfile fullGrantedProcess(Connection c,DrinkFingerprint id,UUID recipient,Set<UUID> visited) throws SQLException {
        if(!visited.add(recipient) || visited.size()>64) return null;
        try(var s=c.prepareStatement("SELECT source_id,legacy_source FROM personal_grants WHERE identity=? AND recipient_id=? AND kind='FULL' ORDER BY source_id")) {
            bind(s,id.value(),recipient.toString());try(var rs=s.executeQuery()) {
                while(rs.next()) {
                    ProcessProfile p=grantSource(c,id,UUID.fromString(rs.getString(1)),rs.getString(2),visited);
                    if(p!=null) return p;
                }
            }
        }return null;
    }
    private ProcessProfile grantSource(Connection c,DrinkFingerprint id,UUID source,String old) throws SQLException {
        return grantSource(c,id,source,old,new HashSet<>());
    }
    private ProcessProfile grantSource(Connection c,DrinkFingerprint id,UUID source,String old,Set<UUID> visited) throws SQLException {
        if(old!=null) {
            try(var q=c.prepareStatement("SELECT process_payload FROM recipe_process_records WHERE fingerprint=?")) {
                bind(q,old);try(var rs=q.executeQuery()) {if(rs.next()) return codec.decode(rs.getBytes(1));}
            }
            return legacy.findByFingerprint(new DrinkFingerprint(old)).orElseThrow().discovery().process();
        }
        try(var s=c.prepareStatement("SELECT process_payload FROM personal_recipes WHERE identity=? AND player_id=?")) {
            bind(s,id.value(),source.toString());try(var rs=s.executeQuery()) { return rs.next() && rs.getBytes(1)!=null?codec.decode(rs.getBytes(1)):fullGrantedProcess(c,id,source,visited); }
        }
    }
    public StoredDiscovery update(long revision,PlayerDiscovery discovery) {
        var snap=discovery.snapshot();return JdbcTransactions.run(connections,true,c->{
            var old=load(c,snap.fingerprint(),snap.discovererId()).orElseThrow(()->new SecurityException("No personal full record"));
            if(old.revision()!=revision || !old.discovery().process().equals(snap.process())) throw new OptimisticLockException();
            if(execute(c,"UPDATE personal_recipes SET personal_name=?,visibility=?,revision=revision+1 WHERE identity=? AND player_id=? AND revision=?",
                snap.canonicalName().map(DrinkName::value).orElse(null),snap.visibility().name(),snap.fingerprint().value(),snap.discovererId().toString(),revision)!=1) throw new OptimisticLockException();
            var sourcePointers=new HashMap<UUID,String>();var modified=new HashMap<UUID,Integer>();
            String currentSource=DrinkFingerprint.from(snap.process()).value();
            execute(c,"INSERT OR IGNORE INTO recipe_process_records VALUES (?,?)",currentSource,codec.encode(snap.process()));
            try(var q=c.prepareStatement("SELECT recipient_id,legacy_source,modified FROM personal_grants WHERE identity=? AND source_id=?")) {
                bind(q,snap.fingerprint().value(),snap.discovererId().toString());try(var rs=q.executeQuery()) {while(rs.next()) {var recipient=UUID.fromString(rs.getString(1));sourcePointers.put(recipient,rs.getString(2));modified.put(recipient,rs.getInt(3));}}
            }
            execute(c,"DELETE FROM personal_grants WHERE identity=? AND source_id=?",snap.fingerprint().value(),snap.discovererId().toString());
            for(var g:snap.grants().entrySet()) {
                execute(c,"INSERT INTO personal_grants VALUES (?,?,?,?,?,?,?)",snap.fingerprint().value(),snap.discovererId().toString(),g.getKey().toString(),g.getValue().full()?"FULL":"PARTIAL",grantCodec.encode(g.getValue()),sourcePointers.getOrDefault(g.getKey(),currentSource),
                    Objects.equals(old.discovery().snapshot().grants().get(g.getKey()),g.getValue())?modified.getOrDefault(g.getKey(),1):1);
                insertRecord(c,snap.fingerprint(),g.getKey(),null,snap.discoveredAt(),snap.temporaryName().value(),null,DiscoveryVisibility.PRIVATE,"LEARNED",false);
            }
            return new StoredDiscovery(discovery,revision+1);
        });
    }
    public RecipeKnowledge knowledgeFor(DrinkFingerprint requested,UUID player) {
        // Old aliases retain their original exact source permissions; never redirect into someone else's process.
        if(legacy.exists(requested)) {
            var old=legacy.findByFingerprint(requested).orElseThrow().discovery();
            if(old.discovererId().equals(player)) return old.knowledgeFor(player);
            boolean originalGrant=JdbcTransactions.run(connections,false,c->{
                try(var q=c.prepareStatement("SELECT modified FROM personal_grants WHERE identity=? AND source_id=? AND recipient_id=?")) {
                    bind(q,FormulationIdentity.from(old.process()).value(),old.discovererId().toString(),player.toString());
                    try(var rs=q.executeQuery()) {return rs.next() && rs.getInt(1)==0;}
                }
            });
            if(originalGrant) return old.knowledgeFor(player);
            var allowed=JdbcTransactions.run(connections,false,c->{
                try(var q=c.prepareStatement("SELECT kind,selection_payload FROM personal_grants WHERE identity=? AND source_id=? AND recipient_id=?")) {
                    bind(q,FormulationIdentity.from(old.process()).value(),old.discovererId().toString(),player.toString());
                    try(var rs=q.executeQuery()) { return rs.next()?Optional.of(grantCodec.decode(rs.getString(1),rs.getString(2))):Optional.<RecipeGrant>empty(); }
                }
            });
            boolean currentlyPublic=JdbcTransactions.run(connections,false,c->{
                try(var q=c.prepareStatement("SELECT visibility FROM personal_recipes WHERE identity=? AND player_id=?")) {
                    bind(q,FormulationIdentity.from(old.process()).value(),old.discovererId().toString());
                    try(var rs=q.executeQuery()) {return rs.next() && rs.getString(1).equals("PUBLIC");}
                }
            });
            if(allowed.isEmpty()) return currentlyPublic && old.visibility()==DiscoveryVisibility.PUBLIC
                ?new RecipeKnowledge.Identified(player,new DiscoverySummary(old.discovererId(),requested,old.discoveredAt(),old.displayName(),old.visibility())):new RecipeKnowledge.Unknown(player);
            var historic=old.knowledgeFor(player);
            if(historic instanceof RecipeKnowledge.Full && allowed.get().full()) return historic;
            var fields=allowed.get().full()?java.util.EnumSet.allOf(ProcessField.class):java.util.EnumSet.copyOf(allowed.get().fields());
            if(historic instanceof RecipeKnowledge.Partial partial) fields.retainAll(partial.disclosure().fields().keySet());
            else if(!(historic instanceof RecipeKnowledge.Full)) return historic;
            if(fields.isEmpty()) return new RecipeKnowledge.Unknown(player);
            return new RecipeKnowledge.Partial(player,new DiscoverySummary(old.discovererId(),requested,old.discoveredAt(),old.displayName(),DiscoveryVisibility.SHARED),RecipeDisclosure.from(old.process(),fields));
        }
        return JdbcTransactions.run(connections,false,c->{
            var id=resolve(c,requested);var own=load(c,id,player);
            if(own.isPresent()) return own.get().discovery().knowledgeFor(player);
            try(var s=c.prepareStatement("SELECT source_id,kind,selection_payload,legacy_source FROM personal_grants WHERE identity=? AND recipient_id=? ORDER BY source_id")) {
                bind(s,id.value(),player.toString());try(var rs=s.executeQuery()) {
                    if(rs.next()) {
                        UUID source=UUID.fromString(rs.getString(1));var process=grantSource(c,id,source,rs.getString(4));
                        if(process==null) return new RecipeKnowledge.Unknown(player);
                        var summary=new DiscoverySummary(source,id,Instant.EPOCH,new DescriptiveNameGenerator().generate(process),DiscoveryVisibility.SHARED);
                        var grant=grantCodec.decode(rs.getString(2),rs.getString(3));
                        return grant.full()?new RecipeKnowledge.Full(player,summary,process):new RecipeKnowledge.Partial(player,summary,RecipeDisclosure.from(process,grant.fields()));
                    }
                }
            }
            var summary=publicSummary(c,id);
            return summary.<RecipeKnowledge>map(s->new RecipeKnowledge.Identified(player,s)).orElseGet(()->new RecipeKnowledge.Unknown(player));
        });
    }
    public List<StoredDiscovery> findOwnedBy(UUID player,int limit,int offset) {
        if(limit<1||limit>200||offset<0) throw new IllegalArgumentException("invalid page");
        return JdbcTransactions.run(connections,false,c->{
            var result=new ArrayList<StoredDiscovery>();
            try(var s=c.prepareStatement("SELECT identity FROM personal_recipes WHERE player_id=? ORDER BY identity LIMIT ? OFFSET ?")) {
                bind(s,player.toString(),limit,offset);try(var rs=s.executeQuery()) { while(rs.next()) load(c,new DrinkFingerprint(rs.getString(1)),player).ifPresent(result::add); }
            }return List.copyOf(result);
        });
    }
    public String recordInfo(DrinkFingerprint requested,UUID player) {
        return JdbcTransactions.run(connections,false,c->{
            var id=resolve(c,requested);String result="Unknown";
            try(var s=c.prepareStatement("SELECT provenance,best_quality FROM personal_recipes WHERE identity=? AND player_id=?")) {
                bind(s,id.value(),player.toString());try(var rs=s.executeQuery()) { if(rs.next()) {double best=rs.getDouble(2);boolean unknown=rs.wasNull();result=rs.getString(1)+"; personal best "+(unknown?"not recorded":Math.round(best)+"%");} }
            }
            try(var s=c.prepareStatement("SELECT player_id FROM server_firsts WHERE identity=?")) {
                bind(s,id.value());try(var rs=s.executeQuery()) { if(rs.next()) result+="; server first "+rs.getString(1); }
            }return result;
        });
    }
    public List<me.arcanebrewery.arcaneBrewery.application.discovery.OwnedDiscoveryView> personalCatalog(UUID player,int limit,int offset) {
        if(limit<1||limit>200||offset<0) throw new IllegalArgumentException("invalid page");
        return JdbcTransactions.run(connections,false,c->{
            var result=new ArrayList<me.arcanebrewery.arcaneBrewery.application.discovery.OwnedDiscoveryView>();
            try(var q=c.prepareStatement("SELECT identity,personal_name,temporary_name,recorded_at,visibility FROM personal_recipes WHERE player_id=? ORDER BY identity LIMIT ? OFFSET ?")) {
                bind(q,player.toString(),limit,offset);try(var rs=q.executeQuery()) {while(rs.next()) result.add(new me.arcanebrewery.arcaneBrewery.application.discovery.OwnedDiscoveryView(player,
                    new DrinkFingerprint(rs.getString(1)),new DrinkName(rs.getString(2)==null?rs.getString(3):rs.getString(2)),Instant.parse(rs.getString(4)),DiscoveryVisibility.valueOf(rs.getString(5)),rs.getString(2)!=null));}
            }return List.copyOf(result);
        });
    }
    public List<DrinkFingerprint> accessibleIds(UUID player) {
        return JdbcTransactions.run(connections,false,c->{
            var result=new ArrayList<DrinkFingerprint>();
            try(var s=c.prepareStatement("SELECT identity FROM personal_recipes WHERE player_id=? ORDER BY identity")) {
                bind(s,player.toString());try(var rs=s.executeQuery()) { while(rs.next()) result.add(new DrinkFingerprint(rs.getString(1))); }
            }return List.copyOf(result);
        });
    }
    public Optional<String> personalName(UUID player,DrinkFingerprint requested) {
        return JdbcTransactions.run(connections,false,c->{
            var id=resolve(c,requested);
            try(var s=c.prepareStatement("SELECT personal_name,temporary_name FROM personal_recipes WHERE identity=? AND player_id=?")) {
                bind(s,id.value(),player.toString());try(var rs=s.executeQuery()) {return rs.next()?Optional.of(rs.getString(1)==null?rs.getString(2):rs.getString(1)):Optional.empty();}
            }
        });
    }
    public Optional<StoredDiscovery> findByFingerprint(DrinkFingerprint id) { return legacy.findByFingerprint(id); }
    public boolean exists(DrinkFingerprint id) { return JdbcTransactions.run(connections,false,c->{
        try(var s=c.prepareStatement("SELECT 1 FROM personal_recipes WHERE identity=?")) {bind(s,resolve(c,id).value());try(var rs=s.executeQuery()){return rs.next();}}
    }); }
    private Optional<DiscoverySummary> publicSummary(Connection c,DrinkFingerprint id) throws SQLException {
        try(var s=c.prepareStatement("SELECT player_id,recorded_at,temporary_name,personal_name FROM personal_recipes WHERE identity=? AND visibility='PUBLIC' ORDER BY recorded_at,player_id LIMIT 1")) {
            bind(s,id.value());try(var rs=s.executeQuery()) { return rs.next()?Optional.of(new DiscoverySummary(UUID.fromString(rs.getString(1)),id,Instant.parse(rs.getString(2)),
                new DrinkName(rs.getString(4)==null?rs.getString(3):rs.getString(4)),DiscoveryVisibility.PUBLIC)):Optional.empty(); }
        }
    }
    public Optional<DiscoverySummary> findPublicSummary(DrinkFingerprint id) { return JdbcTransactions.run(connections,false,c->publicSummary(c,resolve(c,id))); }
    public List<DiscoverySummary> findPublicSummaries(int limit,int offset) {
        if(limit<1||limit>200||offset<0) throw new IllegalArgumentException("invalid page");
        return JdbcTransactions.run(connections,false,c->{
            var result=new ArrayList<DiscoverySummary>();try(var s=c.prepareStatement("SELECT DISTINCT identity FROM personal_recipes WHERE visibility='PUBLIC' ORDER BY identity LIMIT ? OFFSET ?")) {
                bind(s,limit,offset);try(var rs=s.executeQuery()) {while(rs.next()) publicSummary(c,new DrinkFingerprint(rs.getString(1))).ifPresent(result::add);}
            }return List.copyOf(result);
        });
    }
}
