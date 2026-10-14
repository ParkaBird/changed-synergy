package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.SocialAudienceGoal;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extras' automatic bedside teleport must not replace camp or player control. */
@Pseudo
@Mixin(targets = "com.katt.changedextras.common.LatexCuddleHelper", remap = false)
public abstract class ChangedExtrasCuddleMixin {
    @Inject(method = "getTamingOwner", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void changedSynergy$preserveAssignedBehavior(ChangedEntity creature, CallbackInfoReturnable<Player> callback) {
        if (TrueTransfurCompat.isInstinctBody(creature) || PlayerOutpostService.hasAssignment(creature)
                || creature.isSleeping() || creature.isPassenger() || creature.isVehicle()
                || SocialAudienceGoal.isActive(creature) || TakeoverService.carrying(creature)
                || ChangedAddonCompat.isGrabberBusy(creature) || GrabEntityAbility.getGrabber(creature) != null
                || creature.getPersistentData().hasUUID("ChangedSynergyRestOwner")) {
            callback.setReturnValue(null);
            return;
        }
        var grab = BondedSuitService.ability(creature);
        if (grab != null && grab.grabbedEntity != null) callback.setReturnValue(null);
    }
}
