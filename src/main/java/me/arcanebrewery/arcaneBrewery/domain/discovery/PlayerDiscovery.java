package me.arcanebrewery.arcaneBrewery.domain.discovery;

import me.arcanebrewery.arcaneBrewery.domain.drink.DrinkFingerprint;
import me.arcanebrewery.arcaneBrewery.domain.process.ProcessProfile;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Authoritative aggregate for trusted application code; distribute knowledgeFor projections to recipients. */
public final class PlayerDiscovery {
    private final UUID discovererId;
    private final DrinkFingerprint fingerprint;
    private final ProcessProfile process;
    private final Instant discoveredAt;
    private final DrinkName temporaryName;
    private final Optional<DrinkName> canonicalName;
    private final DiscoveryVisibility visibility;
    private final Map<UUID, Grant> grants;

    private record Grant(Optional<RecipeDisclosure> partial) {
        private Grant { Objects.requireNonNull(partial); }
        static Grant full() { return new Grant(Optional.empty()); }
        static Grant partial(RecipeDisclosure disclosure) { return new Grant(Optional.of(disclosure)); }
    }

    private PlayerDiscovery(UUID discovererId, DrinkFingerprint fingerprint, ProcessProfile process,
            Instant discoveredAt, DrinkName temporaryName, Optional<DrinkName> canonicalName,
            DiscoveryVisibility visibility, Map<UUID, Grant> grants) {
        this.discovererId = discovererId;
        this.fingerprint = fingerprint;
        this.process = process;
        this.discoveredAt = discoveredAt;
        this.temporaryName = temporaryName;
        this.canonicalName = canonicalName;
        this.visibility = visibility;
        this.grants = Map.copyOf(grants);
    }

    public static PlayerDiscovery discover(UUID discovererId, ProcessProfile process, Instant discoveredAt) {
        Objects.requireNonNull(discovererId, "discovererId");
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(discoveredAt, "discoveredAt");
        return new PlayerDiscovery(discovererId, DrinkFingerprint.from(process), process, discoveredAt,
                new DescriptiveNameGenerator().generate(process), Optional.empty(),
                DiscoveryVisibility.PRIVATE, Map.of());
    }

    public static PlayerDiscovery personal(UUID player, ProcessProfile process, Instant time) {
        return new PlayerDiscovery(player, me.arcanebrewery.arcaneBrewery.domain.drink.FormulationIdentity.from(process),
                process,time,new DescriptiveNameGenerator().generate(process),Optional.empty(),DiscoveryVisibility.PRIVATE,Map.of());
    }

    public UUID discovererId() { return discovererId; }
    public DrinkFingerprint fingerprint() { return fingerprint; }
    public ProcessProfile process() { return process; }
    public Instant discoveredAt() { return discoveredAt; }
    public DrinkName temporaryName() { return temporaryName; }
    public Optional<DrinkName> canonicalName() { return canonicalName; }
    public boolean renameUsed() { return canonicalName.isPresent(); }
    public DrinkName displayName() { return canonicalName.orElse(temporaryName); }
    public DiscoveryVisibility visibility() { return visibility; }

    /** Trusted state export. Player-facing code must continue using knowledgeFor instead. */
    public DiscoverySnapshot snapshot() {
        Map<UUID, RecipeGrant> exported = new HashMap<>();
        grants.forEach((recipient, grant) -> exported.put(recipient, grant.partial()
                .map(disclosure -> RecipeGrant.partial(disclosure.fields().keySet()))
                .orElseGet(RecipeGrant::fullRecipe)));
        return new DiscoverySnapshot(discovererId, fingerprint, process, discoveredAt, temporaryName,
                canonicalName, visibility, exported);
    }

    public static PlayerDiscovery restore(DiscoverySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Map<UUID, Grant> restored = new HashMap<>();
        snapshot.grants().forEach((recipient, grant) -> restored.put(recipient, grant.full()
                ? Grant.full() : Grant.partial(RecipeDisclosure.from(snapshot.process(), grant.fields()))));
        return new PlayerDiscovery(snapshot.discovererId(), snapshot.fingerprint(), snapshot.process(),
                snapshot.discoveredAt(), snapshot.temporaryName(), snapshot.canonicalName(),
                snapshot.visibility(), restored);
    }

    public PlayerDiscovery rename(UUID actor, String name) {
        requireOwner(actor);
        if(name!=null && name.indexOf('§')>=0) throw new IllegalArgumentException("new names cannot contain formatting codes");
        if (renameUsed())
            throw new IllegalStateException("legacy discovery has already been renamed");
        return copy(Optional.of(new DrinkName(name)), visibility, grants);
    }

    public PlayerDiscovery shareFull(UUID actor, UUID recipient) {
        requireOwner(actor);
        requireOtherPlayer(recipient);
        return grant(recipient, Grant.full());
    }

    /** Replaces the recipient's previous grant, including any full access. Does not accumulate fields. */
    public PlayerDiscovery sharePartial(UUID actor, UUID recipient, Set<ProcessField> fields) {
        requireOwner(actor);
        requireOtherPlayer(recipient);
        return grant(recipient, Grant.partial(RecipeDisclosure.from(process, fields)));
    }

    public PlayerDiscovery revoke(UUID actor, UUID recipient) {
        requireOwner(actor);
        requireOtherPlayer(recipient);
        Map<UUID, Grant> updated = new HashMap<>(grants);
        updated.remove(recipient);
        DiscoveryVisibility next = visibility == DiscoveryVisibility.SHARED && updated.isEmpty()
                ? DiscoveryVisibility.PRIVATE : visibility;
        return copy(canonicalName, next, updated);
    }

    /** PRIVATE clears all grants; PUBLIC reveals metadata only; SHARED requires at least one grant. */
    public PlayerDiscovery changeVisibility(UUID actor, DiscoveryVisibility next) {
        requireOwner(actor);
        Objects.requireNonNull(next, "visibility");
        if (next == DiscoveryVisibility.SHARED && grants.isEmpty())
            throw new IllegalStateException("shared visibility requires a recipient");
        return copy(canonicalName, next, next == DiscoveryVisibility.PRIVATE ? Map.of() : grants);
    }

    public RecipeKnowledge knowledgeFor(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (discovererId.equals(playerId)) return new RecipeKnowledge.Full(playerId, summary(), process);
        Grant grant = grants.get(playerId);
        if (grant != null) {
            return grant.partial().<RecipeKnowledge>map(disclosure ->
                    new RecipeKnowledge.Partial(playerId, summary(), disclosure))
                    .orElseGet(() -> new RecipeKnowledge.Full(playerId, summary(), process));
        }
        return visibility == DiscoveryVisibility.PUBLIC
                ? new RecipeKnowledge.Identified(playerId, summary()) : new RecipeKnowledge.Unknown(playerId);
    }

    private DiscoverySummary summary() {
        return new DiscoverySummary(discovererId, fingerprint, discoveredAt, displayName(), visibility);
    }

    private PlayerDiscovery grant(UUID recipient, Grant grant) {
        Map<UUID, Grant> updated = new HashMap<>(grants);
        updated.put(recipient, grant);
        DiscoveryVisibility next = visibility == DiscoveryVisibility.PRIVATE
                ? DiscoveryVisibility.SHARED : visibility;
        return copy(canonicalName, next, updated);
    }

    private PlayerDiscovery copy(Optional<DrinkName> name, DiscoveryVisibility next, Map<UUID, Grant> updated) {
        return new PlayerDiscovery(discovererId, fingerprint, process, discoveredAt, temporaryName, name, next, updated);
    }

    private void requireOwner(UUID actor) {
        if (!discovererId.equals(Objects.requireNonNull(actor, "actor")))
            throw new SecurityException("only the original discoverer may change this discovery");
    }

    private void requireOtherPlayer(UUID recipient) {
        if (discovererId.equals(Objects.requireNonNull(recipient, "recipient")))
            throw new IllegalArgumentException("owner knowledge and ownership cannot be granted or revoked");
    }
}
