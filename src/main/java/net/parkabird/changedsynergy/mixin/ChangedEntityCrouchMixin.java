package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caps tall NPC Changed crouching height below 2.0 blocks so they can duck under standard doorways. */
@Mixin(ChangedEntity.class)
public abstract class ChangedEntityCrouchMixin {
    @Inject(
            method = {
                "getDimensions(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;",
                "m_6972_(Lnet/minecraft/world/entity/Pose;)Lnet/minecraft/world/entity/EntityDimensions;"
            },
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void changedSynergy$fitTallCrouchBelowTwoBlocks(
            Pose pose, CallbackInfoReturnable<EntityDimensions> callback) {
        if (pose != Pose.CROUCHING) return;
        ChangedEntity self = (ChangedEntity) (Object) this;
        if (self.getUnderlyingPlayer() != null || self.isVehicle()) return;
        EntityDimensions dims = callback.getReturnValue();
        if (dims != null && !dims.fixed && dims.height >= 1.9F) {
            callback.setReturnValue(EntityDimensions.scalable(dims.width, 1.85F));
        }
    }
}
