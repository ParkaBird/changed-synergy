package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** Server-authoritative pat reaction owned entirely by Synergy. */
public final class SynergyPatService {
    private static final String NEXT_REACTION =
            "ChangedSynergyNextNativePatReaction";

    private SynergyPatService() {
    }

    public static boolean perform(
            ServerPlayer actor,
            LivingEntity target,
            boolean animate) {
        if (!actor.isAlive()
                || !target.isAlive()
                || TrueTransfurCompat.isInstinctShell(actor) || TrueTransfurCompat.isRebirthing(actor)
                || target instanceof ServerPlayer recipientPlayer
                        && (TrueTransfurCompat.isInstinctShell(recipientPlayer) || TrueTransfurCompat.isRebirthing(recipientPlayer))
                || target instanceof ChangedEntity body && TrueTransfurCompat.isInstinctBody(body)
                        && TrueTransfurCompat.bodyOwner(body) == null
                || target instanceof ChangedEntity creature && TakeoverService.carrying(creature)
                || target instanceof Player player && TakeoverService.active(player)
                || actor.level() != target.level()
                || actor.distanceToSqr(target) > 36.0D
                || !actor.hasLineOfSight(target)) {
            return false;
        }

        // Pat the visible proxy, but route player feedback to its real owner.
        ServerPlayer recipient = target instanceof ServerPlayer player ? player
                : target instanceof ChangedEntity body
                ? TrueTransfurCompat.bodyOwner(body) : null;

        long now = actor.level().getGameTime();
        if (actor.getPersistentData().getLong(NEXT_REACTION) > now) {
            return false;
        }
        actor.getPersistentData().putLong(NEXT_REACTION, now + 4L);
        if (animate) {
            PatAnimationService.startFixed(actor, target, 4);
        }

        boolean aggressive = target instanceof ChangedEntity creature
                && !creature.isSleeping()
                && CreatureSocialProfile.isAggressive(creature);
        // NPC affection hearts are emitted by onPatted only when a gain is awarded.
        // Player-to-player contact and aggressive appeals retain their own feedback.
        if (target.level() instanceof ServerLevel level
                && (recipient != null || !(target instanceof ChangedEntity) || aggressive)) {
            level.sendParticles(
                    aggressive ? ParticleTypes.CLOUD : ParticleTypes.HEART,
                    target.getX(), target.getY(0.8D), target.getZ(),
                    5, 0.22D, 0.28D, 0.22D, 0.02D);
        }
        if (!aggressive) {
            actor.displayClientMessage(Component.translatable(
                    "message.changed_synergy.pat.given",
                    target.getDisplayName()), true);
        }
        if (recipient != null) {
            ServerPlayer player = recipient;
            HypnosisQteService.reactToPlayerPat(player, actor);
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.pat.received",
                    actor.getDisplayName()), true);
        }
        if (recipient == null && target instanceof ChangedEntity creature) {
            LatexSocialEvents.onPatted(creature, actor);
        }
        return true;
    }
}
