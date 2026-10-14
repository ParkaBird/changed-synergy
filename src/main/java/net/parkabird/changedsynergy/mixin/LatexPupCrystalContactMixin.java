package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.block.LatexPupCrystal;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.parkabird.changedsynergy.event.LatexPupCrystalContact;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LatexPupCrystal.class)
public abstract class LatexPupCrystalContactMixin
        implements LatexPupCrystalContact.DecisionAccess {
    @Invoker(value = "makeAssimilationDecision", remap = false)
    public abstract LatexAssimilationDecision<?> changedSynergy$crystalDecision(
            LivingEntity target);

    @Inject(method = {"entityInside", "m_7892_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$consistentPlayerContact(
            BlockState state, Level level, BlockPos position, Entity entity,
            CallbackInfo callback) {
        if (entity instanceof ServerPlayer player
                && LatexPupCrystalContact.eligible(player)) {
            LatexPupCrystalContact.touch(player, position, state);
            // The player-tick fallback covers faces vanilla never calls here.
            // One shared tick marker prevents doubling the original damage.
            callback.cancel();
        }
    }
}
