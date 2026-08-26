package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the saddled taur's player passenger as its vanilla vehicle controller. */
@Mixin(Mob.class)
public abstract class CentaurControllingPassengerMixin {
    @Inject(
            method = {
                    "getControllingPassenger()Lnet/minecraft/world/entity/LivingEntity;",
                    "m_6688_()Lnet/minecraft/world/entity/LivingEntity;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$centaurController(
            CallbackInfoReturnable<LivingEntity> callback) {
        Entity self = (Mob)(Object)this;
        if (self instanceof ChangedEntity centaur
                && CentaurMountService.isCentaur(centaur)
                && CentaurMountService.hasSaddle(centaur)
                && centaur.getFirstPassenger() instanceof Player rider) {
            callback.setReturnValue(rider);
        }
    }
}
