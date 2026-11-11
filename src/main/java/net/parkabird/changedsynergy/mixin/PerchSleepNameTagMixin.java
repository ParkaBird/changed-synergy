package net.parkabird.changedsynergy.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderer.class)
public abstract class PerchSleepNameTagMixin {
    @ModifyExpressionValue(method={"renderNameTag","m_7649_"}, at={
            @At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getNameTagOffsetY()F"),
            @At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;m_278726_()F")}, remap=false,require=1)
    private float changedSynergy$uprightSleepLabel(float original, Entity entity) {
        // Sleeping has a flat bed hitbox, while a perched bird still has an upright head.
        return entity instanceof LivingEntity living && PerchSleepService.bird(living) && PerchSleepService.perch(living)!=null
                ? living.getDimensions(Pose.STANDING).height + .25F : original;
    }
}
