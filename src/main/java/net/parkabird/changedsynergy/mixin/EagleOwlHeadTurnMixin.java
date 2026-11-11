package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.EagleOwlTraits;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Keeps the player's torso still until its owl head reaches the same native 135-degree limit. */
@Mixin(LivingEntity.class)
public abstract class EagleOwlHeadTurnMixin {
    @ModifyConstant(method={"tickHeadTurn","m_5632_"},constant=@Constant(floatValue=50F),remap=false,require=1)
    private float changedSynergy$owlHeadLimit(float value){return EagleOwlTraits.body((LivingEntity)(Object)this)!=null?135:value;}
    @ModifyConstant(method={"tickHeadTurn","m_5632_"},constant=@Constant(intValue=50),remap=false,require=1)
    private int changedSynergy$owlBodyFollowLimit(int value){return EagleOwlTraits.body((LivingEntity)(Object)this)!=null?135:value;}
}
