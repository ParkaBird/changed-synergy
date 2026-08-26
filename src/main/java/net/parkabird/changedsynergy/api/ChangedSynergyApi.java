package net.parkabird.changedsynergy.api;

import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.BondReleaseService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexSocialRelation;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Stable entry points for optional integrations with Synergy relationships. */
public final class ChangedSynergyApi {
    private ChangedSynergyApi() {
    }

    public static LatexSocialRelation relationship(
            ChangedEntity creature,
            ServerPlayer player) {
        return LatexSocialRelation.between(creature, player);
    }

    public static boolean isBonded(ChangedEntity creature, ServerPlayer player) {
        return LatexSocialMemory.isBonded(creature, player);
    }

    public static boolean shouldRemainNeutral(
            ChangedEntity creature,
            ServerPlayer player) {
        return LatexSocialMemory.shouldRemainNeutral(creature, player);
    }

    public static boolean canTarget(ChangedEntity creature, ServerPlayer player) {
        return NpcDispositionEvents.canTarget(creature, player);
    }

    public static Set<UUID> bondedCreatures(ServerPlayer player) {
        return Set.copyOf(LatexSocialMemory.bondedCreatureUuids(player));
    }

    public static BondReleaseService.Result releaseBond(
            ServerPlayer player,
            UUID creatureUuid) {
        return BondReleaseService.release(player, creatureUuid);
    }
}
