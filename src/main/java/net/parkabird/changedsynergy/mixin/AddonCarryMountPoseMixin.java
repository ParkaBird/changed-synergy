package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Addon's carry pose from mistaking a taur rider for a held target. */
@Pseudo
@Mixin(
        targets = "net.foxyas.changedaddon.client.model.animations.CarryAbilityAnimation",
        remap = false)
public abstract class AddonCarryMountPoseMixin {
    @Inject(
            method = "hasPassenger",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$ignoreTaurRider(
            ChangedEntity entity,
            CallbackInfoReturnable<Boolean> callback) {
        if (CentaurMountService.isCentaur(entity)
                && CentaurMountService.hasSaddle(entity)
                && entity.getFirstPassenger() instanceof Player) {
            callback.setReturnValue(false);
        }
    }
}
