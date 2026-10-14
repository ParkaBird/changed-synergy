package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class TakeoverCameraTurnMixin {
    @Inject(method={"turn(DD)V","m_19884_(DD)V"}, at=@At("HEAD"), cancellable=true, remap=false, require=1)
    private void takeover$observe(double yaw,double pitch,CallbackInfo callback) {
        if ((Object)this == Minecraft.getInstance().player && TakeoverClientState.movementLocked()) {
            // Let MouseHandler drain its deltas even in first person, so F5
            // cannot apply a backlog of ignored mouse movement all at once.
            TakeoverClientState.turnObservation(yaw,pitch);
            callback.cancel();
        }
    }
}
