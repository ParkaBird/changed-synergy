package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives Synergy taurs the same steering hooks used by vanilla horses. */
@Mixin(LivingEntity.class)
public abstract class CentaurRideControlMixin {
    @Inject(
            method = {
                    "getRiddenInput(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;",
                    "m_274312_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$horseInput(
            Player rider,
            Vec3 originalInput,
            CallbackInfoReturnable<Vec3> callback) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof ChangedEntity centaur)
                || !isControlledCentaur(centaur, rider)) {
            return;
        }
        float sideways = rider.xxa * 0.5F;
        float forward = rider.zza;
        if (forward <= 0.0F) {
            forward *= 0.25F;
        }
        callback.setReturnValue(new Vec3(sideways, 0.0D, forward));
    }

    @Inject(
            method = {
                    "getRiddenSpeed(Lnet/minecraft/world/entity/player/Player;)F",
                    "m_245547_(Lnet/minecraft/world/entity/player/Player;)F"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$horseSpeed(
            Player rider,
            CallbackInfoReturnable<Float> callback) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self instanceof ChangedEntity centaur
                && isControlledCentaur(centaur, rider)) {
            callback.setReturnValue(CentaurMountService.riddenSpeed(centaur));
        }
    }

    @Inject(
            method = {
                    "tickRidden(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)V",
                    "m_274498_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;)V"
            },
            at = @At("TAIL"),
            remap = false)
    private void changedSynergy$tickHorseRide(
            Player rider,
            Vec3 riddenInput,
            CallbackInfo callback) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof ChangedEntity centaur)
                || !isControlledCentaur(centaur, rider)) {
            return;
        }

        centaur.setYRot(rider.getYRot());
        centaur.yRotO = centaur.getYRot();
        centaur.setXRot(rider.getXRot() * 0.5F);
        centaur.setYHeadRot(centaur.getYRot());
        centaur.setYBodyRot(centaur.getYRot());
        centaur.setTarget(null);
        centaur.setAggressive(false);
        centaur.getNavigation().stop();

        // This is a one-shot charged jump. Leaving LivingEntity#jumping set
        // while airborne makes vanilla jumpFromGround fire again on landing.
        centaur.setJumping(false);
        if (!centaur.isControlledByLocalInstance() || !centaur.onGround()) {
            return;
        }
        Float jumpScale = CentaurMountService.consumeChargedJump(centaur);
        if (jumpScale == null) {
            return;
        }

        MobEffectInstance jumpBoost = centaur.getEffect(MobEffects.JUMP);
        double boost = jumpBoost == null
                ? 0.0D : 0.1D * (jumpBoost.getAmplifier() + 1);
        Vec3 movement = centaur.getDeltaMovement();
        centaur.setDeltaMovement(
                movement.x,
                CentaurMountService.riddenJumpStrength(centaur)
                        * jumpScale + boost,
                movement.z);
        centaur.hasImpulse = true;
        ForgeHooks.onLivingJump(centaur);

        if (riddenInput.z > 0.0D) {
            float sin = Mth.sin(centaur.getYRot() * Mth.DEG_TO_RAD);
            float cos = Mth.cos(centaur.getYRot() * Mth.DEG_TO_RAD);
            centaur.setDeltaMovement(centaur.getDeltaMovement().add(
                    -0.4F * sin * jumpScale,
                    0.0D,
                    0.4F * cos * jumpScale));
        }
    }

    private static boolean isControlledCentaur(
            ChangedEntity centaur,
            Player rider) {
        return CentaurMountService.isCentaur(centaur)
                && CentaurMountService.hasSaddle(centaur)
                && centaur.getFirstPassenger() == rider;
    }
}
