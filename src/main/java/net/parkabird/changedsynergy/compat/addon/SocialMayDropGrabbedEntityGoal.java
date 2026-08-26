package net.parkabird.changedsynergy.compat.addon;

import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.foxyas.changedaddon.entity.ai.goals.abilities.MayDropGrabbedEntityGoal;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.minecraft.server.level.ServerPlayer;

/** Keeps protected friendly suits and secondary-transfur holds intact through old combat entries. */
public final class SocialMayDropGrabbedEntityGoal extends MayDropGrabbedEntityGoal {
    private final ChangedEntity mob;
    private final IGrabberEntity grabber;

    public SocialMayDropGrabbedEntityGoal(ChangedEntity mob, IGrabberEntity grabber) {
        super(grabber);
        this.mob = mob;
        this.grabber = grabber;
    }

    @Override
    public boolean canUse() {
        GrabEntityAbilityInstance ability = grabber.getGrabAbilityInstance();
        if (ability != null && ability.grabbedEntity instanceof ServerPlayer player
                && (LatexSocialMemory.isFriendlySuitActive(mob, player)
                        || BondedSuitService.isNativeOwnerSuit(mob, player)
                        || LatexSocialMemory.isSecondaryGrabActive(mob, player)
                        || LatexSocialMemory.isFriendlyArmHoldTarget(mob, player))) {
            return false;
        }
        return super.canUse();
    }
}
