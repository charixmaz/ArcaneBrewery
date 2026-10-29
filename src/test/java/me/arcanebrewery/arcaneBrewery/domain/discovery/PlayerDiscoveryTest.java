package me.arcanebrewery.arcaneBrewery.domain.discovery;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static me.arcanebrewery.arcaneBrewery.domain.discovery.DiscoveryFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDiscoveryTest {
    @Test
    void newDiscoveryBelongsOnlyToDiscovererAndDefaultsPrivate() {
        var discovery = discovery();
        assertEquals(OWNER, discovery.discovererId());
        assertEquals(DISCOVERED_AT, discovery.discoveredAt());
        assertEquals(DiscoveryVisibility.PRIVATE, discovery.visibility());
        assertEquals(RecipeKnowledge.State.FULL, discovery.knowledgeFor(OWNER).state());
        assertEquals(new RecipeKnowledge.Unknown(FRIEND), discovery.knowledgeFor(FRIEND));
        assertEquals(RecipeKnowledge.State.UNKNOWN, discovery.knowledgeFor(STRANGER).state());
        assertTrue(discovery.canonicalName().isEmpty());
        assertFalse(discovery.renameUsed());
    }

    @Test
    void generatedTemporaryNameIsDeterministic() {
        assertEquals(discovery().temporaryName(), discovery().temporaryName());
        assertEquals("Unnamed Elixir", discovery().displayName().value());
    }

    @Test
    void oneRenameSucceedsAndPreservesIdentityOriginalOwnerAndTemporaryName() {
        var original = discovery();
        var renamed = original.rename(OWNER, "  Moonlit   Reserve  ");
        assertEquals("Moonlit Reserve", renamed.canonicalName().orElseThrow().value());
        assertEquals(renamed.canonicalName().orElseThrow(), renamed.displayName());
        assertTrue(renamed.renameUsed());
        assertEquals(original.fingerprint(), renamed.fingerprint());
        assertEquals(original.process(), renamed.process());
        assertEquals(original.discovererId(), renamed.discovererId());
        assertEquals(original.discoveredAt(), renamed.discoveredAt());
        assertEquals(original.temporaryName(), renamed.temporaryName());
        assertFalse(original.renameUsed());
        assertThrows(IllegalStateException.class, () -> renamed.rename(OWNER, "Another Name"));
    }

    @Test
    void invalidRenameDoesNotConsumeRename() {
        var discovery = discovery();
        assertThrows(IllegalArgumentException.class, () -> discovery.rename(OWNER, "\t"));
        assertFalse(discovery.renameUsed());
        assertTrue(discovery.rename(OWNER, "Valid").renameUsed());
    }

    @Test
    void onlyOwnerMayRenameShareRevokeOrChangeVisibility() {
        var discovery = discovery();
        assertThrows(SecurityException.class, () -> discovery.rename(FRIEND, "Stolen"));
        assertThrows(SecurityException.class, () -> discovery.shareFull(FRIEND, STRANGER));
        assertThrows(SecurityException.class, () -> discovery.sharePartial(FRIEND, STRANGER,
                Set.of(ProcessField.AGING_WOOD)));
        assertThrows(SecurityException.class, () -> discovery.revoke(FRIEND, OWNER));
        assertThrows(SecurityException.class, () -> discovery.changeVisibility(FRIEND, DiscoveryVisibility.PUBLIC));
    }

    @Test
    void fullSharingGrantsExactReproducibleProcessWithoutTransferringOwnership() {
        var original = discovery();
        var shared = original.shareFull(OWNER, FRIEND);
        var knowledge = assertInstanceOf(RecipeKnowledge.Full.class, shared.knowledgeFor(FRIEND));
        assertEquals(FRIEND, knowledge.playerId());
        assertEquals(RecipeKnowledge.State.FULL, knowledge.state());
        assertEquals(original.process(), knowledge.process());
        assertTrue(new ReproductionVerifier().reproduces(original, knowledge.process()));
        assertEquals(OWNER, knowledge.discovery().discovererId());
        assertEquals(OWNER, shared.discovererId());
        assertEquals(DiscoveryVisibility.SHARED, shared.visibility());
        assertEquals(RecipeKnowledge.State.UNKNOWN, shared.knowledgeFor(STRANGER).state());
        assertEquals(RecipeKnowledge.State.UNKNOWN, original.knowledgeFor(FRIEND).state());
        assertThrows(SecurityException.class, () -> shared.rename(FRIEND, "New Owner"));
        assertThrows(SecurityException.class, () -> shared.shareFull(FRIEND, STRANGER));
    }

    @Test
    void partialSharingDisclosesOnlySelectedFields() {
        var shared = discovery().sharePartial(OWNER, FRIEND,
                Set.of(ProcessField.AGING_WOOD, ProcessField.PREPARATION_METHOD));
        var partial = assertInstanceOf(RecipeKnowledge.Partial.class, shared.knowledgeFor(FRIEND));
        assertEquals(RecipeKnowledge.State.PARTIAL, partial.state());
        assertEquals(Set.of(ProcessField.AGING_WOOD, ProcessField.PREPARATION_METHOD),
                partial.disclosure().fields().keySet());
        assertEquals(new RecipeDisclosure.Text("oak"), partial.disclosure().fields().get(ProcessField.AGING_WOOD));
        assertFalse(partial.disclosure().fields().containsKey(ProcessField.INGREDIENT_RATIOS));
        assertEquals(OWNER, partial.discovery().discovererId());
    }

    @Test
    void revocationRemovesFullAndPartialGrantsAndLeavesOwnerKnowledge() {
        var shared = discovery().shareFull(OWNER, FRIEND)
                .sharePartial(OWNER, STRANGER, Set.of(ProcessField.AGING_WOOD));
        var revoked = shared.revoke(OWNER, FRIEND).revoke(OWNER, STRANGER);
        assertEquals(RecipeKnowledge.State.UNKNOWN, revoked.knowledgeFor(FRIEND).state());
        assertEquals(RecipeKnowledge.State.UNKNOWN, revoked.knowledgeFor(STRANGER).state());
        assertEquals(RecipeKnowledge.State.FULL, revoked.knowledgeFor(OWNER).state());
        assertEquals(DiscoveryVisibility.PRIVATE, revoked.visibility());
        assertEquals(OWNER, revoked.discovererId());
        assertEquals(RecipeKnowledge.State.FULL, shared.knowledgeFor(FRIEND).state());
    }

    @Test
    void ownerKnowledgeCannotBeGrantedOrRevoked() {
        var discovery = discovery();
        assertThrows(IllegalArgumentException.class, () -> discovery.shareFull(OWNER, OWNER));
        assertThrows(IllegalArgumentException.class, () -> discovery.sharePartial(OWNER, OWNER,
                Set.of(ProcessField.AGING_WOOD)));
        assertThrows(IllegalArgumentException.class, () -> discovery.revoke(OWNER, OWNER));
        assertEquals(RecipeKnowledge.State.FULL, discovery.knowledgeFor(OWNER).state());
    }

    @Test
    void publicVisibilityRevealsMetadataButNotRecipeOrOwnership() {
        var discovery = discovery().changeVisibility(OWNER, DiscoveryVisibility.PUBLIC);
        var identified = assertInstanceOf(RecipeKnowledge.Identified.class, discovery.knowledgeFor(STRANGER));
        assertEquals(RecipeKnowledge.State.IDENTIFIED, identified.state());
        assertEquals(discovery.fingerprint(), identified.discovery().fingerprint());
        assertEquals(discovery.displayName(), identified.discovery().displayName());
        assertEquals(OWNER, identified.discovery().discovererId());
        assertEquals(RecipeKnowledge.State.FULL, discovery.knowledgeFor(OWNER).state());
        assertEquals(OWNER, discovery.discovererId());
    }

    @Test
    void revokingPublicGrantFallsBackToIdentificationAndRetainsPublicVisibility() {
        var discovery = discovery().changeVisibility(OWNER, DiscoveryVisibility.PUBLIC)
                .shareFull(OWNER, FRIEND).revoke(OWNER, FRIEND);
        assertEquals(RecipeKnowledge.State.IDENTIFIED, discovery.knowledgeFor(FRIEND).state());
        assertEquals(DiscoveryVisibility.PUBLIC, discovery.visibility());
    }

    @Test
    void returningToPrivateClearsAllGrants() {
        var discovery = discovery().shareFull(OWNER, FRIEND)
                .changeVisibility(OWNER, DiscoveryVisibility.PUBLIC)
                .changeVisibility(OWNER, DiscoveryVisibility.PRIVATE);
        assertEquals(RecipeKnowledge.State.UNKNOWN, discovery.knowledgeFor(FRIEND).state());
        assertEquals(RecipeKnowledge.State.UNKNOWN, discovery.knowledgeFor(STRANGER).state());
        assertEquals(RecipeKnowledge.State.FULL, discovery.knowledgeFor(OWNER).state());
    }

    @Test
    void partialGrantReplacesPreviousGrantInsteadOfAccumulatingSecrets() {
        var discovery = discovery().shareFull(OWNER, FRIEND)
                .sharePartial(OWNER, FRIEND, Set.of(ProcessField.AGING_WOOD))
                .sharePartial(OWNER, FRIEND, Set.of(ProcessField.PREPARATION_METHOD));
        var partial = assertInstanceOf(RecipeKnowledge.Partial.class, discovery.knowledgeFor(FRIEND));
        assertEquals(Set.of(ProcessField.PREPARATION_METHOD), partial.disclosure().fields().keySet());
        assertEquals(RecipeKnowledge.State.FULL, discovery.shareFull(OWNER, FRIEND).knowledgeFor(FRIEND).state());
    }

    @Test
    void renameUseAndOwnershipSurviveEveryAccessTransition() {
        var discovery = discovery().rename(OWNER, "Permanent")
                .shareFull(OWNER, FRIEND)
                .sharePartial(OWNER, STRANGER, Set.of(ProcessField.AGING_WOOD))
                .changeVisibility(OWNER, DiscoveryVisibility.PUBLIC)
                .revoke(OWNER, FRIEND).revoke(OWNER, STRANGER)
                .changeVisibility(OWNER, DiscoveryVisibility.PRIVATE);
        assertEquals(OWNER, discovery.discovererId());
        assertEquals("Permanent", discovery.displayName().value());
        assertTrue(discovery.renameUsed());
        assertThrows(IllegalStateException.class, () -> discovery.rename(OWNER, "Again"));
    }

    @Test
    void invalidActorsRecipientsAndVisibilityAreRejected() {
        var discovery = discovery();
        assertThrows(NullPointerException.class, () -> discovery.rename(null, "Name"));
        assertThrows(NullPointerException.class, () -> discovery.shareFull(OWNER, null));
        assertThrows(NullPointerException.class, () -> discovery.knowledgeFor(null));
        assertThrows(NullPointerException.class, () -> discovery.changeVisibility(OWNER, null));
        assertThrows(IllegalStateException.class, () -> discovery.changeVisibility(OWNER, DiscoveryVisibility.SHARED));
        assertThrows(NullPointerException.class, () -> PlayerDiscovery.discover(null, discovery.process(), DISCOVERED_AT));
        assertThrows(NullPointerException.class, () -> PlayerDiscovery.discover(OWNER, null, DISCOVERED_AT));
        assertThrows(NullPointerException.class, () -> PlayerDiscovery.discover(OWNER, discovery.process(), null));
    }
}
