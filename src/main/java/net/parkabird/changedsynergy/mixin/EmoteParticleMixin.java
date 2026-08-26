package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.effect.particle.EmoteParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.client.EmoteClientState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces stale bubbles immediately and removes them with their tracked entity. */
@Mixin(value = EmoteParticle.class, remap = false)
public abstract class EmoteParticleMixin {
    @Shadow(remap = false)
    @Final
    private Entity track;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void changedSynergy$registerCurrentEmote(CallbackInfo callback) {
        EmoteClientState.register(track, (Particle)(Object)this);
    }

    @Inject(
            method = {"tick", "m_5989_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 1)
    private void changedSynergy$removeDeadCreatureEmote(CallbackInfo callback) {
        Particle particle = (Particle)(Object)this;
        if (!EmoteClientState.isCurrent(track, particle)
                || track.isRemoved()
                || track instanceof LivingEntity living && !living.isAlive()) {
            EmoteClientState.release(track, particle);
            ((Particle) (Object) this).remove();
            callback.cancel();
        }
    }

    @Inject(
            method = {"tick", "m_5989_"},
            at = @At("TAIL"),
            remap = false,
            require = 1)
    private void changedSynergy$releaseExpiredEmote(CallbackInfo callback) {
        Particle particle = (Particle)(Object)this;
        if (!particle.isAlive()) {
            EmoteClientState.release(track, particle);
        }
    }
}
