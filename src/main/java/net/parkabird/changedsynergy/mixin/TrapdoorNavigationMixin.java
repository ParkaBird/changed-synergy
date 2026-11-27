package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.Mob;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.CompanionOpenDoorGoal;
import net.parkabird.changedsynergy.ai.NavigationObstacleSteering;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class TrapdoorNavigationMixin {
    @Group(name = "trapdoorSteering", min = 1, max = 1)
    @Inject(method = {"serverAiStep", "m_6140_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V", shift = At.Shift.AFTER), remap = false, require = 0)
    private void changedSynergy$trapdoorSteering(CallbackInfo callback) {
        if ((Object)this instanceof ChangedEntity creature && creature.getUnderlyingPlayer() == null
                && !TrueTransfurCompat.isInstinctBody(creature)
                && BondedSuitService.getSuitController(creature) == null) {
            if (CompanionOpenDoorGoal.tickAutoCrouch(creature)) {
                NavigationObstacleSteering.clearStuck(creature);
            }
            var move = creature.getMoveControl();
            double wx = move.getWantedX(), wz = move.getWantedZ();
            CompanionOpenDoorGoal.slidePastOpenTrapdoor(creature);
            if (move.getWantedX() == wx && move.getWantedZ() == wz) {
                NavigationObstacleSteering.tickSteering(creature);
            }
        }
    }

    @Group(name = "trapdoorSteering", min = 1, max = 1)
    @Inject(method = {"serverAiStep", "m_6140_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_7638_()V", shift = At.Shift.AFTER), remap = false, require = 0)
    private void changedSynergy$trapdoorSteeringProduction(CallbackInfo callback) {
        changedSynergy$trapdoorSteering(callback);
    }
}
