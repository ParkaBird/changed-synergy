package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Let the carried player look around while the companion chooses the path. */
@Mixin(value = Camera.class, priority = 900)
public abstract class SharedRestCameraMixin {
    @Shadow(aliases = "m_90572_")
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = {"setup", "m_90575_"}, at = @At("TAIL"), remap = false)
    private void changedSynergy$restCarryLook(BlockGetter level, Entity entity,
            boolean thirdPerson, boolean mirrored, float partialTick, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (player != null && entity instanceof ChangedEntity carrier
                && FriendlySocialHugState.isLocked(carrier.getId(), player.getId())) {
            setRotation(player.getViewYRot(partialTick), player.getViewXRot(partialTick));
        }
    }
}
