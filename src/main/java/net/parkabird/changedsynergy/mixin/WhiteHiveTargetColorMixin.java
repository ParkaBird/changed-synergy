package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.client.PureWhiteVisionClientState;
import net.parkabird.changedsynergy.client.ScoutTargetClientState;
import net.parkabird.changedsynergy.client.WhiteHiveTargetClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps hive target outlines white even when a scoreboard team has a color. */
@Mixin(value = Entity.class, remap = false)
public abstract class WhiteHiveTargetColorMixin {
    @Inject(
            method = {"getTeamColor", "m_19876_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void changedSynergy$useWhiteHiveOutline(
            CallbackInfoReturnable<Integer> callback) {
        Entity entity = (Entity)(Object)this;
        if (WhiteHiveTargetClientState.isMarked(entity)
                || ScoutTargetClientState.isMarked(entity)) {
            callback.setReturnValue(0xFFFFFF);
        } else if (PureWhiteVisionClientState.active()
                && entity instanceof LivingEntity) {
            if (entity instanceof Player) {
                callback.setReturnValue(0xFF668A);
            } else if (entity instanceof ChangedEntity changed) {
                callback.setReturnValue(
                        HunterFaction.of(changed) == HunterFaction.WHITE
                                ? 0x52E5FF : 0xFFC34D);
            } else {
                callback.setReturnValue(0x78F58C);
            }
        }
    }
}
