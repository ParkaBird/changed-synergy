package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Service queries use server sessions or packet-maintained client entity markers.
 * Entity persistent NBT is NOT automatically synced; the packet receiver owns it.
 * No client-only class or integrated-server session map is consulted here.
 * Higher priority lets cancellation precede GrabQteSyncMixin's release capture/stun.
 * Service must establish the suit before publishing active/carrying; only its
 * scoped recovery call may make allowsNativeRelease true (clear in finally).
 * Cancellation cannot prevent Addon callers from sending RELEASE packets after
 * this void method returns. Client markers also gate release, but direct packet
 * field writes still require filtering/resync. Clear BOTH participant markers
 * before applying an authoritative client release; active markers never bypass.
 */
@Mixin(value = GrabEntityAbilityInstance.class, remap = false, priority = 1100)
public abstract class TakeoverGrabMixin {
    @Unique
    private boolean changedSynergy$takeoverParticipant(LivingEntity participant) {
        if (participant == null) {
            return false;
        }
        // On a remote client the authoritative session map is intentionally empty;
        // TakeoverStatePacket maintains this marker on both rendered participants.
        if (participant.getPersistentData().getBoolean("SynergyTakeoverClient")) return true;
        return participant instanceof Player player && TakeoverService.active(player)
                || participant instanceof ChangedEntity carrier && TakeoverService.carrying(carrier);
    }

    @Unique
    private boolean changedSynergy$takeoverLocked() {
        GrabEntityAbilityInstance grab = (GrabEntityAbilityInstance)(Object)this;
        return changedSynergy$takeoverParticipant(grab.entity.getEntity())
                || changedSynergy$takeoverParticipant(grab.grabbedEntity);
    }

    @Unique
    private boolean changedSynergy$serverRestoring() {
        GrabEntityAbilityInstance grab = (GrabEntityAbilityInstance)(Object)this;
        return !grab.entity.getLevel().isClientSide()
                && grab.entity.getEntity() instanceof ChangedEntity carrier
                && TakeoverService.allowsNativeRelease(carrier);
    }

    @Inject(method = "handleEscape()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void changedSynergy$blockNativeEscape(CallbackInfo callback) {
        if (changedSynergy$takeoverLocked()) {
            GrabEntityAbilityInstance grab = (GrabEntityAbilityInstance)(Object)this;
            grab.currentEscapeKey = null;
            grab.lastEscapeKey = null;
            grab.ticksUnpressed = 0;
            grab.escapeKeys.reset(false);
            callback.cancel();
        }
    }

    @Inject(method = {"grabEntity(Lnet/minecraft/world/entity/LivingEntity;)Z",
            "suitEntity(Lnet/minecraft/world/entity/LivingEntity;)Z"},
            at = @At("HEAD"), cancellable = true, require = 1)
    private void changedSynergy$blockNestedGrab(LivingEntity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (changedSynergy$takeoverLocked() || changedSynergy$takeoverParticipant(target)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "tickIdle()V", at = {
            @At("HEAD"),
            @At(value = "FIELD", target = "Lnet/ltxprogrammer/changed/ability/GrabEntityAbilityInstance;attackDown:Z", opcode = 180),
            @At(value = "FIELD", target = "Lnet/ltxprogrammer/changed/ability/GrabEntityAbilityInstance;useDown:Z", opcode = 180)
    }, require = 1)
    private void changedSynergy$clearNativeInputs(CallbackInfo callback) {
        if (changedSynergy$takeoverLocked()) {
            GrabEntityAbilityInstance grab = (GrabEntityAbilityInstance)(Object)this;
            grab.attackDown = false;
            grab.useDown = false;
        }
        // Do NOT cancel tickIdle: retain native position, noPhysics and suit sync.
        // GETFIELD gates also clear input sampled again by the local-player branch
        // after HEAD. Public fields may be set by native packets / Addon goals.
    }

    @Inject(method = "makeAssimilationDecision()Lnet/ltxprogrammer/changed/entity/ai/LatexAssimilationDecision;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void changedSynergy$blockNativeAssimilation(
            CallbackInfoReturnable<LatexAssimilationDecision<?>> callback) {
        if (changedSynergy$takeoverLocked()) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "stopUsing()V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void changedSynergy$blockManualControl(CallbackInfo callback) {
        if (changedSynergy$takeoverLocked()) {
            callback.cancel();
        }
    }

    @Inject(method = "releaseEntity(Z)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void changedSynergy$blockExternalRelease(boolean applyDebuffs, CallbackInfo callback) {
        if (changedSynergy$takeoverLocked() && !changedSynergy$serverRestoring()) {
            callback.cancel();
        }
    }

    @Inject(method = "replaceEntityReference(Lnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void changedSynergy$blockExternalReplacement(LivingEntity replacement, CallbackInfo callback) {
        // Native REPLACE packets mutate the link without passing releaseEntity.
        if ((changedSynergy$takeoverLocked() || changedSynergy$takeoverParticipant(replacement))
                && !changedSynergy$serverRestoring()) {
            callback.cancel();
        }
    }

    @Inject(method = "canGrabbedEntityBeStolen()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void changedSynergy$blockStealing(CallbackInfoReturnable<Boolean> callback) {
        if (changedSynergy$takeoverLocked()) {
            callback.setReturnValue(false);
        }
    }
}
