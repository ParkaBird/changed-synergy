package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.BasicPlayerInfo;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.InheritedEyeAppearance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies an inherited eye snapshot without changing the player's global appearance settings. */
@Mixin(value = ChangedEntity.class, remap = false)
public abstract class ChangedEntityInheritedEyesMixin {
    @Inject(method = "getBasicPlayerInfo", at = @At("RETURN"), cancellable = true, require = 1)
    private void changedSynergy$useInheritedEyes(
            CallbackInfoReturnable<BasicPlayerInfo> callback) {
        ChangedEntity self = (ChangedEntity)(Object)this;
        Player player = self.getUnderlyingPlayer();
        if (player == null) {
            return;
        }
        var instance = ProcessTransfur.getPlayerTransfurVariant(player);
        if (instance == null || instance.isTemporaryFromSuit()
                || instance.getChangedEntity() != self) {
            return;
        }
        BasicPlayerInfo merged = InheritedEyeAppearance.merge(
                callback.getReturnValue(), instance);
        if (merged != null) {
            callback.setReturnValue(merged);
        }
    }
}
