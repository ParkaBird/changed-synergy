package net.parkabird.changedsynergy.mixin;

import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class HypnosisEffectGuardMixin {
    @Inject(
            method = {
                "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
                "m_147207_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$guardHypnosisEffects(
            MobEffectInstance effect,
            Entity source,
            CallbackInfoReturnable<Boolean> callback) {
        if (HypnosisQteService.shouldBlockHypnosisEffect(
                (LivingEntity)(Object)this, effect, source)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = {
                "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
                "m_147207_(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false)
    private void changedSynergy$observeDirectHypnosisEffects(
            MobEffectInstance effect,
            Entity source,
            CallbackInfoReturnable<Boolean> callback) {
        LivingEntity target = (LivingEntity)(Object)this;
        if (target instanceof ServerPlayer player && source instanceof LivingEntity hypnotist) {
            HypnosisQteService.observeAppliedHypnosis(
                    hypnotist, player, effect, callback.getReturnValue());
        }
    }
}
