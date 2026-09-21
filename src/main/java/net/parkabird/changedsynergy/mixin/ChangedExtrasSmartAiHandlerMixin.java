package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.parkabird.changedsynergy.compat.ChangedExtrasCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents Changed Extras from replacing goals on Synergy-owned creatures. */
@Pseudo
@Mixin(
        targets = "com.katt.changedextras.common.ai.LatexMobAIHandler",
        remap = false)
public abstract class ChangedExtrasSmartAiHandlerMixin {
    @Inject(
            method = "onEntityJoin",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void changedSynergy$yieldJoin(
            EntityJoinLevelEvent event,
            CallbackInfo callback) {
        if (event.getEntity() instanceof ChangedEntity mob
                && ChangedExtrasCompat.smartAiEnabled(mob)
                && ChangedExtrasCompat.shouldYieldSmartAi(mob)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "onLivingTick",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private static void changedSynergy$yieldTick(
            LivingEvent.LivingTickEvent event,
            CallbackInfo callback) {
        if (event.getEntity() instanceof ChangedEntity mob
                && ChangedExtrasCompat.smartAiEnabled(mob)
                && ChangedExtrasCompat.shouldYieldSmartAi(mob)) {
            callback.cancel();
        }
    }
}
