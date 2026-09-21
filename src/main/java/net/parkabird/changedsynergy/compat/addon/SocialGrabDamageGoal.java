package net.parkabird.changedsynergy.compat.addon;

import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Supplies every non-organic social creature with deterministic grapple damage.
 * Friendly suits and the two scripted re-assimilation holds keep their own
 * behavior and are never fed through this pulse.
 */
public final class SocialGrabDamageGoal extends Goal {
    private static final double DAMAGE_INTERVAL_MULTIPLIER = 4.0D / 3.0D;
    private static final double PLAYER_DAMAGE_INTERVAL_MULTIPLIER = 1.5D;
    private static final long PLAYER_REACTION_TICKS = 20L;

    private final ChangedEntity mob;
    private final IGrabberEntity grabber;
    private int cooldown;
    private LivingEntity observedHeld;
    private long firstPlayerPulseAt;
    private boolean playerPulse;

    public SocialGrabDamageGoal(ChangedEntity mob, IGrabberEntity grabber) {
        this.mob = mob;
        this.grabber = grabber;
    }

    @Override
    public boolean canUse() {
        GrabEntityAbilityInstance currentAbility = grabber.getGrabAbilityInstance();
        LivingEntity currentHeld = currentAbility == null ? null : currentAbility.grabbedEntity;
        if (currentHeld != observedHeld) {
            observedHeld = currentHeld;
            firstPlayerPulseAt = mob.level().getGameTime() + PLAYER_REACTION_TICKS;
        }
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!mob.isAlive() || HypnosisProfile.isHypnoticCreature(mob)) {
            return false;
        }
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if (ability == null || ability.suited
                || ability.grabbedEntity == null) {
            return false;
        }
        LivingEntity held = ability.grabbedEntity;
        if (held instanceof ServerPlayer player) {
            return mob.level().getGameTime() >= firstPlayerPulseAt
                    && !LatexSocialMemory.isOrganic(mob)
                    && !LatexSocialMemory.isFriendlyArmHoldTarget(mob, player)
                    && LatexSocialMemory.mayInitiateHostileGrab(mob, player)
                    && !LatexSocialMemory.isSecondaryGrabActive(mob, player);
        }

        // Addon's stock damage goal can pulse against any held living entity.
        // The social replacement used to narrow that to ServerPlayer, turning
        // combat grabs against mobs into self-stuns. Preserve the social
        // friendly-fire filters while restoring damage for an actual target.
        if (!held.isAlive() || held.isRemoved() || held == mob
                || mob.isAlliedTo(held) || !mob.canAttack(held)
                || held instanceof ChangedEntity changed
                        && LatexCreatureCombatRules.mustRejectTarget(
                                mob, changed)) {
            return false;
        }
        return grabber.canCauseGrabDamage()
                && (mob.getTarget() == held
                        || held.getLastHurtByMob() == mob
                        || LatexSocialMemory.isPetDefenseAuthorized(
                                mob, held));
    }

    @Override
    public void start() {
        playerPulse = observedHeld instanceof ServerPlayer;
        grabber.setCausingGrabDamage(true);
    }

    @Override
    public void tick() {
        // The Addon mixin normally ticks once from the entity itself. Calling
        // through the public bridge here also covers entities whose own tick
        // path omits that injected branch.
        grabber.mayTickGrabAbility();
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void stop() {
        grabber.setCausingGrabDamage(false);
        int baseCooldown = Math.max(5, grabber.getGrabDamageCooldown());
        cooldown = (int)Math.ceil(baseCooldown * (playerPulse
                ? PLAYER_DAMAGE_INTERVAL_MULTIPLIER : DAMAGE_INTERVAL_MULTIPLIER));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
