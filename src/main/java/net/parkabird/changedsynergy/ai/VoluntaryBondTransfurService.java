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
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

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
        return canTransfur(creature, player) && BondTokenService.ready(creature, player);
    }

    public static boolean canOfferFriendly(ChangedEntity creature, ServerPlayer player) {
        return canTransfur(creature, player) && CreaturePersonality.familiarity(creature, player) >= 60
                && !BondTokenService.canCreateBond(creature, player) && !BondTokenService.reservedElsewhere(creature, player);
    }

    private static boolean canTransfur(ChangedEntity creature, ServerPlayer player) {
        return player.isAlive() && !player.isSpectator()
                && !net.parkabird.changedsynergy.origin.NativeOriginService.pending(player)
                && creature.isAlive()
                && !creature.isRemoved()
                && creature.level() == player.level()
                && player.distanceToSqr(creature) <= 64.0D
                && player.hasLineOfSight(creature)
                && !net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(player)
                && !TakeoverService.active(player) && !TakeoverService.carrying(creature)
                && net.ltxprogrammer.changed.ability.GrabEntityAbility.getGrabber(player) == null
                && !LatexSocialMemory.isFriendlySuitActive(creature, player)
                && LatexSocialMemory.isSocialLatex(creature)
                && CreatureSocialProfile.allowsSocialWheel(creature)
                && CreaturePersonality.hasTrustedRelationship(creature, player)
                && CreaturePersonality.relationshipTier(creature, player)
                        == CreaturePersonality.RelationshipTier.CLOSE
                && !LatexSocialMemory.isProvoked(creature, player)
                && !LatexSocialMemory.hasBetrayedPatTruce(creature, player)
                && !FactionReputation.isHostile(creature, player)
                && !ProcessTransfur.isPlayerTransfurred(player)
                && creature.getSelfVariant() != null;
    }

    /**
     * The first selection arms a short confirmation window. Selecting the same
     * creature again performs the transfur and may establish a bond.
     */
    public static boolean select(
            ChangedEntity creature,
            ServerPlayer player) {
        return select(creature, player, false);
    }

    public static boolean select(ChangedEntity creature, ServerPlayer player, boolean friendlyOnly) {
        if (!(friendlyOnly ? canOfferFriendly(creature, player) : canOffer(creature, player))) {
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
                && confirmation.getBoolean("FriendlyOnly") == friendlyOnly
                && confirmation.getLong(EXPIRES) >= now;
        if (!confirmed) {
            confirmation.putUUID(TARGET, creature.getUUID());
            confirmation.putBoolean("FriendlyOnly", friendlyOnly);
            confirmation.putLong(EXPIRES, now + CONFIRMATION_WINDOW);
            root.put(CONFIRMATION, confirmation);
            creature.getNavigation().stop();
            creature.getLookControl().setLookAt(player, 30.0F, 30.0F);
            player.displayClientMessage(Component.translatable(
                    friendlyOnly ? "message.changed_synergy.social.friendly_transfur_confirm"
                            : "message.changed_synergy.social.voluntary_transfur_confirm",
                    creature.getDisplayName()), true);
            NpcDialogue.trigger(
                    creature, player, Cue.VOLUNTARY_BOND_CONFIRM);
            return false;
        }

        clearConfirmation(player);
        return complete(creature, player, friendlyOnly);
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
            ServerPlayer player, boolean friendlyOnly) {
        // Recheck after the confirmation click. A bond or transfur may have
        // appeared while the wheel was still open.
        if (!(friendlyOnly ? canOfferFriendly(creature, player) : canOffer(creature, player))) {
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
            boolean createBond = !friendlyOnly && BondTokenService.canCreateBond(creature, player);
            if (createBond) {
                LatexSocialMemory.addBond(creature, player);
            }
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
            if (createBond) {
                NpcDialogue.trigger(creature, player, Cue.VOLUNTARY_BOND_COMPLETE);
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.changed_synergy.social.voluntary_transfur_no_bond"), true);
            }
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
        } else if (CreaturePersonality.familiarity(creature, player) < 60
                && (CreaturePersonality.balanced() || ChangedSynergyConfig.COMMON.bondTokensRequired.get())
                || CreaturePersonality.relationshipTier(creature, player) != CreaturePersonality.RelationshipTier.CLOSE) {
            key = "message.changed_synergy.social.voluntary_transfur_requires_close";
        } else if (ChangedSynergyConfig.COMMON.bondTokensRequired.get() && BondTokenService.canCreateBond(creature, player)
                && !CreaturePersonality.recognizesBond(creature, player) && !BondTokenService.reservedFor(creature, player)) {
            key = "message.changed_synergy.social.voluntary_transfur_requires_token";
        } else {
            key = "message.changed_synergy.social.voluntary_transfur_unavailable";
        }
        player.displayClientMessage(Component.translatable(key), true);
    }

    private static void clearConfirmation(ServerPlayer player) {
        player.getPersistentData().remove(CONFIRMATION);
    }
}
