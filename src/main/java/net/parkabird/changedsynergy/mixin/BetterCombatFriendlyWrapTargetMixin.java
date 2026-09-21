package net.parkabird.changedsynergy.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.client.FriendlySuitClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Better Combat from treating the hidden friendly wrapper as a target. */
@Pseudo
@Mixin(targets = "net.bettercombat.client.collision.TargetFinder", remap = false)
public abstract class BetterCombatFriendlyWrapTargetMixin {
    @Inject(method = "getInitialTargets", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private static void changedSynergy$excludeFriendlyWrapper(
            Player player,
            Entity cursorTarget,
            double range,
            CallbackInfoReturnable<List<Entity>> cir) {
        if (!FriendlySuitClientState.isOwnerSuited(player.getId())) {
            return;
        }
        Entity wrapper = FriendlySuitClientState.getSuitingCreature(player.getId());
        List<Entity> targets = cir.getReturnValue();
        if (wrapper == null || targets == null || !targets.contains(wrapper)) {
            return;
        }
        List<Entity> filtered = new ArrayList<>(targets);
        filtered.removeIf(entity -> entity == wrapper);
        cir.setReturnValue(filtered);
    }
}
