package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Friendly social latex should not make a nearby bed unsafe to use. */
@Mixin(ChangedEntity.class)
public abstract class FriendlyLatexSleepMixin {
    @Inject(
            method = {"isPreventingPlayerRest", "m_6935_"},
            at = @At("HEAD"),
            cancellable = true)
    private void changedSynergy$friendlyLatexAllowsRest(
            Player player,
            CallbackInfoReturnable<Boolean> callback) {
        Object self = this;
        if (self instanceof ChangedEntity creature
                && player instanceof ServerPlayer serverPlayer
                && LatexSocialMemory.isSocialLatex(creature)
                && CreatureSocialProfile.allowsSynergySystems(creature)
                && !NpcDispositionEvents.hasHostileDisposition(
                        creature, serverPlayer)) {
            callback.setReturnValue(false);
        }
    }
}
