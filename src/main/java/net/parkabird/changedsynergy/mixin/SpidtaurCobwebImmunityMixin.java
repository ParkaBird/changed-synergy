package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.entity.LatexSpidtaur;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Matches Spider Tiger cobweb passage immunity for both transfurred and Instinct Spidtaur players. */
@Mixin(Player.class)
public abstract class SpidtaurCobwebImmunityMixin {
    @Inject(method = {"makeStuckInBlock", "m_7601_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$ignoreCobwebSlowdown(BlockState state, Vec3 motionMultiplier, CallbackInfo ci) {
        if (!state.is(Blocks.COBWEB)) {
            return;
        }
        Player self = (Player)(Object)this;
        var body = IAbstractChangedEntity.forEitherSafe(self)
                .map(IAbstractChangedEntity::getChangedEntity)
                .orElse(null);
        if (body instanceof LatexSpidtaur) {
            ci.cancel();
        }
    }
}
