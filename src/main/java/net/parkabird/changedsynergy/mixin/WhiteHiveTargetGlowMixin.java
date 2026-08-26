package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.client.PureWhiteVisionClientState;
import net.parkabird.changedsynergy.client.ScoutTargetClientState;
import net.parkabird.changedsynergy.client.WhiteHiveTargetClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the hive-shared outline only for the transformed white player. */
@Mixin(value = Minecraft.class, remap = false)
public abstract class WhiteHiveTargetGlowMixin {
    @Inject(
            method = {"shouldEntityAppearGlowing", "m_91314_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void changedSynergy$showHiveTarget(
            Entity entity,
            CallbackInfoReturnable<Boolean> callback) {
        if (WhiteHiveTargetClientState.isMarked(entity)
                || ScoutTargetClientState.isMarked(entity)) {
            callback.setReturnValue(true);
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (PureWhiteVisionClientState.active()
                && entity instanceof LivingEntity living
                && living.isAlive()
                && minecraft.player != null
                && entity != minecraft.player
                && minecraft.player.distanceToSqr(entity) <= 40.0D * 40.0D
                && minecraft.player.hasLineOfSight(entity)) {
            // This is contrast for visible bodies, not the removed through-wall
            // same-faction sensing mechanic.
            callback.setReturnValue(true);
        }
    }
}
