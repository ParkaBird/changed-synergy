package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.entity.MuntjacJumpAccess;
import net.parkabird.changedsynergy.entity.MuntjacJumpService;
import net.parkabird.changedsynergy.entity.MuntjacJumpState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MuntjacPlayerJumpMixin implements MuntjacJumpAccess {
    @Unique private final MuntjacJumpState changedSynergy$muntjacCharge = new MuntjacJumpState();
    @Override public MuntjacJumpState changedSynergy$muntjacJump() { return changedSynergy$muntjacCharge; }

    @Inject(method = {"jumpFromGround", "m_6135_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$holdJump(CallbackInfo ci) {
        if (MuntjacJumpService.holding((Player)(Object)this)) ci.cancel();
    }

    @Inject(method = {"jumpFromGround", "m_6135_"}, at = @At("TAIL"), remap = false)
    private void changedSynergy$consumeJump(CallbackInfo ci) { MuntjacJumpService.jumped((Player)(Object)this); }
}
