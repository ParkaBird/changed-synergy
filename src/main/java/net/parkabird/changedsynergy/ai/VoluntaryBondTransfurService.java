package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/**
 * Turns an established friendship into a bond through a deliberate, confirmed
 * transfur. No combat or environmental transfur is allowed to call this path.
 */
public final class VoluntaryBondTransfurService {
    private static final String CONFIRMATION =
            "ChangedSynergyVoluntaryBondConfirmation";
    private static final String IN_PROGRESS =
            "ChangedSynergyVoluntaryBondInProgress";
    private static final String TARGET = "Target";
    private static final String EXPIRES = "Expires";
    private static final long CONFIRMATION_WINDOW = 200L;

    private VoluntaryBondTransfurService() {
    }

    /** Used when the social wheel is opened, and repeated on the server on click. */
    public static boolean canOffer(
            ChangedEntity creature,
            ServerPlayer player) {
        return ChangedSynergyGameRules.enabled(
                        player.level(), ChangedSynergyGameRules.BOND_SYSTEM)
                && creature.isAlive()
                && !creature.isRemoved()
                && creature.level() == player.level()
                && player.distanceToSqr(creature) <= 64.0D
                && LatexSocialMemory.isSocialLatex(creature)
                && CreatureSocialProfile.allowsSocialWheel(creature)
                && CreaturePersonality.hasTrustedRelationship(creature, player)
                && !LatexSocialMemory.isProvoked(creature, player)
                && !LatexSocialMemory.hasBetrayedPatTruce(creature, player)
                && !ProcessTransfur.isPlayerTransfurred(player)
                && creature.getSelfVariant() != null
                && !LatexSocialMemory.hasActiveBond(creature)
                && LatexSocialMemory.bondedCreatureUuids(player).isEmpty();
    }

    /**
     * The first selection arms a short confirmation window. Selecting the same
     * creature again performs the transfur and creates the only Synergy bond.
     */
    public static boolean select(
            ChangedEntity creature,
            ServerPlayer player) {
        if (!canOffer(creature, player)) {
            clearConfirmation(player);
            showUnavailableReason(creature, player);
            return false;
        }

        long now = creature.level().getGameTime();
        CompoundTag root = player.getPersistentData();
        CompoundTag confirmation = root.contains(CONFIRMATION, Tag.TAG_COMPOUND)
                ? root.getCompound(CONFIRMATION) : new CompoundTag();
        boolean confirmed = confirmation.hasUUID(TARGET)
                && creature.getUUID().equals(confirmation.getUUID(TARGET))
                && confirmation.getLong(EXPIRES) >= now;
        if (!confirmed) {
            confirmation.putUUID(TARGET, creature.getUUID());
            confirmation.putLong(EXPIRES, now + CONFIRMATION_WINDOW);
            root.put(CONFIRMATION, confirmation);
            creature.getNavigation().stop();
            creature.getLookControl().setLookAt(player, 30.0F, 30.0F);
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.social.voluntary_transfur_confirm",
                    creature.getDisplayName()), true);
            NpcDialogue.trigger(
                    creature, player, Cue.VOLUNTARY_BOND_CONFIRM);
            return false;
        }

        clearConfirmation(player);
        return complete(creature, player);
    }

    /** True only while this exact friend is performing the confirmed transfur. */
    public static boolean isCompleting(
            ChangedEntity creature,
            ServerPlayer player) {
        CompoundTag progress = player.getPersistentData().getCompound(IN_PROGRESS);
        return progress.hasUUID(TARGET)
                && creature.getUUID().equals(progress.getUUID(TARGET));
    }

    private static boolean complete(
            ChangedEntity creature,
            ServerPlayer player) {
        // Recheck after the confirmation click. A bond or transfur may have
        // appeared while the wheel was still open.
        if (!canOffer(creature, player)) {
            showUnavailableReason(creature, player);
            return false;
        }
        TransfurVariant<?> sourceVariant = creature.getSelfVariant();
        if (sourceVariant == null) {
            return false;
        }

        CompoundTag inProgress = new CompoundTag();
        inProgress.putUUID(TARGET, creature.getUUID());
        player.getPersistentData().put(IN_PROGRESS, inProgress);
        try {
            TransfurContext context = TransfurContext.npcLatexHazard(
                    creature, TransfurCause.GRAB_REPLICATE);
            float progress = player.level().getGameRules()
                    .getBoolean(ChangedGameRules.RULE_DO_TRANSFUR_ANIMATION)
                            ? 0.0F : 1.0F;
            TransfurVariantInstance<?> instance =
                    ProcessTransfur.setPlayerTransfurVariant(
                            player, sourceVariant, context, progress);
            if (instance == null) {
                player.displayClientMessage(Component.translatable(
                        "message.changed_synergy.social.voluntary_transfur_failed"),
                        true);
                return false;
            }

            instance.transfurContext = context;
            instance.transfurProgressionO = progress;
            instance.transfurProgression = progress;
            instance.setTemporaryForSuit(false);
            InvoluntaryTransfurNegotiation.abandonForVoluntaryBond(player);
            LatexSocialMemory.addBond(creature, player);
            LatexSocialEvents.calmTowards(creature, player);
            creature.getNavigation().stop();
            creature.getLookControl().setLookAt(player, 30.0F, 30.0F);

            if (LatexSocialMemory.isOrganic(creature)) {
                ChangedSounds.broadcastSound(
                        player, ChangedSounds.TRANSFUR_BY_NOT_LATEX, 1.0F, 1.0F);
            } else {
                ChangedSounds.broadcastSound(
                        player, sourceVariant.sound, 1.0F, 1.0F);
            }
            if (creature.level() instanceof ServerLevel level) {
                level.sendParticles(
                        ParticleTypes.HEART,
                        creature.getX(),
                        creature.getY(0.78D),
                        creature.getZ(),
                        7, 0.3D, 0.28D, 0.3D, 0.02D);
            }
            NpcDialogue.trigger(
                    creature, player, Cue.VOLUNTARY_BOND_COMPLETE);
            player.closeContainer();
            return true;
        } finally {
            player.getPersistentData().remove(IN_PROGRESS);
        }
    }

    private static void showUnavailableReason(
            ChangedEntity creature,
            ServerPlayer player) {
        String key;
        if (ProcessTransfur.isPlayerTransfurred(player)) {
            key = "message.changed_synergy.social.voluntary_transfur_human_only";
        } else if (!LatexSocialMemory.bondedCreatureUuids(player).isEmpty()) {
            key = "message.changed_synergy.social.voluntary_transfur_already_bonded";
        } else if (LatexSocialMemory.hasActiveBond(creature)) {
            key = "message.changed_synergy.social.voluntary_transfur_creature_bonded";
        } else {
            key = "message.changed_synergy.social.voluntary_transfur_unavailable";
        }
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static void clearConfirmation(ServerPlayer player) {
        player.getPersistentData().remove(CONFIRMATION);
    }
}
