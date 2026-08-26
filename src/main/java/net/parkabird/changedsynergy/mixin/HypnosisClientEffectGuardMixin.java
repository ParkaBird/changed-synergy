package net.parkabird.changedsynergy.mixin;

import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors the server escape window so client-side Changed ticks cannot restore its visuals. */
@Mixin(LivingEntity.class)
public abstract class HypnosisClientEffectGuardMixin {
    @Inject(
            method = {
                "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
                "m_147207_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$guardClientHypnosisEffects(
            MobEffectInstance effect,
            Entity source,
            CallbackInfoReturnable<Boolean> callback) {
        LivingEntity target = (LivingEntity)(Object)this;
        if (target instanceof Player
                && source instanceof LivingEntity hypnotist
                && HypnosisQteClientState.shouldBlockEffect(hypnotist, effect)) {
            callback.setReturnValue(false);
        }
    }
}
