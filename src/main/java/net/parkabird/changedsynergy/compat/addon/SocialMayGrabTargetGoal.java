package net.parkabird.changedsynergy.compat.addon;

import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayGrabTargetGoal;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.minecraft.server.level.ServerPlayer;

/** Prevents Addon's one-tick grab goal from repeatedly raising its arms at protected players. */
public final class SocialMayGrabTargetGoal extends MayGrabTargetGoal {
    private final ChangedEntity mob;

    public SocialMayGrabTargetGoal(ChangedEntity mob, IGrabberEntity grabber) {
        super(grabber);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        return mayAttemptGrab() && super.canUse();
    }

    @Override
    public void start() {
        if (mayAttemptGrab()) {
            super.start();
        }
    }

    @Override
    public void tick() {
        if (mayAttemptGrab()) {
            super.tick();
        }
    }

    private boolean mayAttemptGrab() {
        if (CreatureSocialProfile.isGrabMechanicExcluded(mob)
                || HypnosisProfile.isHypnoticCreature(mob)) {
            return false;
        }
        if (!(mob.getTarget() instanceof ServerPlayer player)) {
            return true;
        }
        if (CreatureCacheGuardService.isDefendingAgainst(mob, player)) {
            // Cache defence is ordinary melee pursuit. Do not let Addon's
            // grab gate clear the target merely because the player is otherwise neutral.
            return false;
        }
        if (LatexFusionIntent.mayInitiate(mob, player)) {
            // Native Changed fusion uses the ordinary melee goal.  This goal
            // must neither start Addon's grab animation nor clear its target;
            // doing either made a friendly knight raise/lower its arms forever.
            return false;
        }
        if (LatexSocialMemory.mayInitiateHostileGrab(mob, player)) {
            return true;
        }
        if (LatexSocialMemory.shouldRemainNeutral(mob, player)) {
            // Clear stale targets too; otherwise another attack goal can restore
            // the target between this one-tick goal's canUse/start phases.
            LatexSocialEvents.calmTowards(mob, player);
        }
        return false;
    }
}
