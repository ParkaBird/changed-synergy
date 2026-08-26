package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.AssimilationBehavior;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/** Shared limits for organic creatures' physical attacks against players. */
public final class OrganicCombatRules {
    private static final float SWIPE_DAMAGE = 1.0F;
    private static final float STANDARD_LATEX_SWIPE_PROGRESS = 3.0F;
    private static final float GRAPPLE_ONLY_HEALTH_FRACTION = 0.30F;
    private static final ThreadLocal<Boolean> COMBINED_SWIPE =
            ThreadLocal.withInitial(() -> false);

    private OrganicCombatRules() {
    }

    /** Low-health players are preserved for assimilation through a grapple. */
    public static boolean shouldUseOnlyGrapple(
            ChangedEntity mob,
            ServerPlayer player) {
        return LatexSocialMemory.isSocialLatex(mob)
                && LatexSocialMemory.isOrganic(mob)
                && player.getHealth() <= grappleOnlyHealth(player);
    }

    public static boolean isOrdinaryMelee(
            DamageSource source,
            ChangedEntity mob) {
        return source.is(DamageTypes.MOB_ATTACK)
                && source.getDirectEntity() == mob;
    }

    /**
     * Keeps an ordinary swipe small and prevents it from crossing the point at
     * which the creature must switch to grapple-only combat.
     */
    public static float limitSwipeDamage(
            ServerPlayer player,
            float requestedDamage) {
        float healthAboveFloor = player.getHealth() - grappleOnlyHealth(player);
        return Math.max(0.0F, Math.min(
                SWIPE_DAMAGE,
                Math.min(requestedDamage, healthAboveFloor)));
    }

    public static boolean applySwipeDamage(
            ChangedEntity mob,
            ServerPlayer player) {
        float damage = limitSwipeDamage(player, SWIPE_DAMAGE);
        return damage > 0.0F
                && player.hurt(mob.damageSources().mobAttack(mob), damage);
    }

    public static boolean usesNativeLatexSwipe(ChangedEntity mob) {
        return mob.getType().is(ChangedTags.EntityTypes.LATEX);
    }

    public static boolean isApplyingCombinedSwipe() {
        return COMBINED_SWIPE.get();
    }

    /**
     * Addon marks several organic ChangedEntity types as non-latex, which makes
     * ChangedEntity.tryTransfurTarget return before creating an assimilation
     * decision. Run the same two parts explicitly for those entities.
     */
    public static void applyCombinedNonLatexSwipe(
            ChangedEntity mob,
            ServerPlayer player) {
        COMBINED_SWIPE.set(true);
        try {
            applySwipeDamage(mob, player);
        } finally {
            COMBINED_SWIPE.remove();
        }

        IAbstractChangedEntity source = IAbstractChangedEntity.forEntity(mob);
        LatexAssimilationDecision<?> decision = mob.makeLatexAssimilationDecision(
                TransfurCause.GRAB_REPLICATE, player);
        if (decision == null) {
            return;
        }
        float progress = ProcessTransfur.checkBlocked(
                player, standardLatexSwipeProgress(mob), source);
        if (progress <= 0.0F) {
            return;
        }
        AssimilationBehavior behavior = ProcessTransfur.computeAssimilationBehavior(
                player, decision.withTransfurProgress(progress));
        if (behavior != null) {
            behavior.stepAssimilate();
        }
    }

    /** Matches the base Changed creature's native TRANSFUR_DAMAGE of one swipe. */
    public static float standardLatexSwipeProgress(ChangedEntity mob) {
        IAbstractChangedEntity source = IAbstractChangedEntity.forEntity(mob);
        return ProcessTransfur.difficultyAdjustTransfurAmount(
                mob.level().getDifficulty(), STANDARD_LATEX_SWIPE_PROGRESS, source);
    }

    private static float grappleOnlyHealth(ServerPlayer player) {
        return player.getMaxHealth() * GRAPPLE_ONLY_HEALTH_FRACTION;
    }
}
