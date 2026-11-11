package net.parkabird.changedsynergy.mixin;

import net.minecraft.core.*;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import net.parkabird.changedsynergy.block.PerchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LivingEntity.class)
public abstract class PerchSleepingPositionMixin {
    @Inject(method={"setPosToBed","m_21080_"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void changedSynergy$onBeam(BlockPos pos,CallbackInfo ci){
        var entity=(LivingEntity)(Object)this;
        if(entity.level().getBlockState(pos).getBlock() instanceof PerchBlock&&PerchSleepService.bird(entity)){
            var point=PerchSleepService.position(entity.level(),pos);entity.setPos(point.x,point.y,point.z);ci.cancel();
        }
    }
    @Inject(method={"getBedOrientation","m_21259_"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void changedSynergy$uprightSleep(CallbackInfoReturnable<Direction> ci){if(PerchSleepService.perch((LivingEntity)(Object)this)!=null)ci.setReturnValue(Direction.UP);}
}
