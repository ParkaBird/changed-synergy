package net.parkabird.changedsynergy.ai;

import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.minecraft.server.level.ServerPlayer;

/** Performs the complete, player-requested end of a creature bond. */
public final class BondReleaseService {
    private static final long PARTING_TRUCE_TICKS = 400L;

    private BondReleaseService() {
    }

    public static Result release(ServerPlayer player, UUID creatureUuid) {
        if (!LatexSocialMemory.bondedCreatureUuids(player).contains(creatureUuid)) {
            return Result.NOT_BONDED;
        }

        ChangedEntity pet = LatexSocialMemory.findLoadedBondedCreature(player, creatureUuid);
        if (pet == null) {
            LatexSocialMemory.queueManualRelease(player, creatureUuid);
            return Result.QUEUED_UNLOADED;
        }

        boolean wasWrapping = BondedSuitService.isWrappingOwner(pet, player)
                || LatexSocialMemory.isFriendlySuitActive(pet, player);
        if (wasWrapping) {
            BondedSuitService.releaseForBondEnd(pet, player);
        }

        // Parting is voluntary: calm both the vanilla target state and Synergy's
        // hunt memory, then leave enough time for the player to walk away safely.
        LatexSocialEvents.calmTowards(pet, player);
        LatexSocialMemory.beginTruce(pet, player, PARTING_TRUCE_TICKS);

        Cue cue = wasWrapping
                ? Cue.BOND_MANUAL_RELEASE_WRAPPING
                : Cue.BOND_MANUAL_RELEASE;
        NpcDialogue.emoteOnly(pet, cue);
        NpcDialogue.triggerBondFarewell(pet, player, cue);
        LatexSocialMemory.unregisterBond(pet, player);
        return Result.RELEASED_LOADED;
    }

    @Nullable
    public static ChangedEntity loadedCreature(ServerPlayer player, UUID creatureUuid) {
        return LatexSocialMemory.findLoadedBondedCreature(player, creatureUuid);
    }

    public enum Result {
        RELEASED_LOADED,
        QUEUED_UNLOADED,
        NOT_BONDED
    }
}
